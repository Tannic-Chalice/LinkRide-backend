package com.linkride.backend.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationPreferenceRepository preferenceRepository;
    @Mock
    private DeviceTokenRepository deviceTokenRepository;
    @Mock
    private DeviceTokenService deviceTokenService;
    @Mock
    private FcmPushSender fcmPushSender;

    private NotificationServiceImpl notificationService;

    private UUID recipientId;
    private NotificationEvent event;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(
                notificationRepository, preferenceRepository, deviceTokenRepository,
                deviceTokenService, fcmPushSender, new NotificationProperties());

        recipientId = UUID.randomUUID();
        event = new NotificationEvent(recipientId, NotificationCategory.BOOKING, "BOOKING_ACCEPTED",
                "title", "body", "BOOKING", UUID.randomUUID());

        // A real save assigns notificationId via GenerationType.UUID immediately (Hibernate 6's
        // before-execution UUID generator) -- the mock must mirror that, since buildDataPayload
        // dereferences the id and a mocked save() otherwise leaves it null.
        lenient().when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> {
                    Notification n = inv.getArgument(0);
                    if (n.getNotificationId() == null) {
                        n.setNotificationId(UUID.randomUUID());
                    }
                    return n;
                });
    }

    @Test
    void createAndDeliver_noPreferenceRow_defaultsEnabled_sendsToEveryActiveToken() {
        when(preferenceRepository.findByUserIdAndCategory(recipientId, NotificationCategory.BOOKING))
                .thenReturn(Optional.empty());

        DeviceToken token1 = deviceToken("token-1");
        DeviceToken token2 = deviceToken("token-2");
        when(deviceTokenRepository.findByUserIdAndActiveTrue(recipientId)).thenReturn(List.of(token1, token2));
        when(fcmPushSender.send(anyString(), anyString(), anyString(), any())).thenReturn(FcmPushSender.PushOutcome.SENT);

        notificationService.createAndDeliver(event);

        verify(fcmPushSender).send(eq("token-1"), anyString(), anyString(), any());
        verify(fcmPushSender).send(eq("token-2"), anyString(), anyString(), any());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    void createAndDeliver_preferenceDisabled_skipsPushEntirely() {
        NotificationPreference preference = new NotificationPreference();
        preference.setPushEnabled(false);
        when(preferenceRepository.findByUserIdAndCategory(recipientId, NotificationCategory.BOOKING))
                .thenReturn(Optional.of(preference));

        notificationService.createAndDeliver(event);

        verify(fcmPushSender, never()).send(anyString(), anyString(), anyString(), any());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getDeliveryStatus()).isEqualTo(DeliveryStatus.SKIPPED);
    }

    @Test
    void createAndDeliver_noActiveTokens_marksSkipped() {
        when(preferenceRepository.findByUserIdAndCategory(recipientId, NotificationCategory.BOOKING))
                .thenReturn(Optional.empty());
        when(deviceTokenRepository.findByUserIdAndActiveTrue(recipientId)).thenReturn(List.of());

        notificationService.createAndDeliver(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getDeliveryStatus()).isEqualTo(DeliveryStatus.SKIPPED);
    }

    @Test
    void createAndDeliver_tokenInvalid_deactivatesTokenAndMarksFailed() {
        when(preferenceRepository.findByUserIdAndCategory(recipientId, NotificationCategory.BOOKING))
                .thenReturn(Optional.empty());
        DeviceToken token = deviceToken("stale-token");
        when(deviceTokenRepository.findByUserIdAndActiveTrue(recipientId)).thenReturn(List.of(token));
        when(fcmPushSender.send(anyString(), anyString(), anyString(), any()))
                .thenReturn(FcmPushSender.PushOutcome.TOKEN_INVALID);

        notificationService.createAndDeliver(event);

        verify(deviceTokenService).deactivateByToken("stale-token");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void createAndDeliver_allTransientFailures_marksFailedWithoutDeactivating() {
        when(preferenceRepository.findByUserIdAndCategory(recipientId, NotificationCategory.BOOKING))
                .thenReturn(Optional.empty());
        DeviceToken token = deviceToken("flaky-token");
        when(deviceTokenRepository.findByUserIdAndActiveTrue(recipientId)).thenReturn(List.of(token));
        when(fcmPushSender.send(anyString(), anyString(), anyString(), any()))
                .thenReturn(FcmPushSender.PushOutcome.TRANSIENT_FAILURE);

        notificationService.createAndDeliver(event);

        verify(deviceTokenService, never()).deactivateByToken(anyString());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void markRead_alreadyRead_isIdempotentNoOp() {
        UUID notificationId = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setNotificationId(notificationId);
        notification.setStatus(NotificationStatus.READ);
        when(notificationRepository.findByNotificationIdAndRecipientUserId(notificationId, recipientId))
                .thenReturn(Optional.of(notification));

        notificationService.markRead(recipientId, notificationId);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void markRead_notFound_throws404() {
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findByNotificationIdAndRecipientUserId(notificationId, recipientId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(recipientId, notificationId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getPreferences_missingRows_fillDefaults() {
        when(preferenceRepository.findByUserId(recipientId)).thenReturn(List.of());

        List<NotificationPreferenceDto> preferences = notificationService.getPreferences(recipientId);

        assertThat(preferences).hasSize(NotificationCategory.values().length);
        assertThat(preferences).allMatch(NotificationPreferenceDto::getPushEnabled);
    }

    @Test
    void listNotifications_delegatesToRepositoryWithCappedPageSize() {
        Page<Notification> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(notificationRepository.findInbox(eq(recipientId), any(), any(), any())).thenReturn(page);

        NotificationPageResponse response = notificationService.listNotifications(recipientId, null, null, 0, 500);

        assertThat(response.getTotalElements()).isZero();
    }

    private DeviceToken deviceToken(String fcmToken) {
        DeviceToken token = new DeviceToken();
        token.setFcmToken(fcmToken);
        token.setUserId(recipientId);
        token.setPlatform(DevicePlatform.ANDROID);
        return token;
    }
}
