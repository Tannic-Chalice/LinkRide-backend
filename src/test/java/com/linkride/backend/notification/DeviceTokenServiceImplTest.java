package com.linkride.backend.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceImplTest {

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    private DeviceTokenServiceImpl deviceTokenService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        deviceTokenService = new DeviceTokenServiceImpl(deviceTokenRepository);
        userId = UUID.randomUUID();
        lenient().when(deviceTokenRepository.save(any(DeviceToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void registerToken_newToken_createsActiveRow() {
        when(deviceTokenRepository.findByFcmToken("new-token")).thenReturn(Optional.empty());

        DeviceTokenResponse response = deviceTokenService.registerToken(userId, "new-token", DevicePlatform.ANDROID);

        assertThat(response.getFcmToken()).isEqualTo("new-token");
        assertThat(response.getActive()).isTrue();
        assertThat(response.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
    }

    @Test
    void registerToken_existingToken_upsertsOwnerAndReactivates() {
        DeviceToken existing = new DeviceToken();
        existing.setFcmToken("existing-token");
        existing.setUserId(UUID.randomUUID());
        existing.setActive(false);
        when(deviceTokenRepository.findByFcmToken("existing-token")).thenReturn(Optional.of(existing));

        DeviceTokenResponse response = deviceTokenService.registerToken(userId, "existing-token", DevicePlatform.ANDROID);

        assertThat(response.getActive()).isTrue();
        ArgumentCaptor<DeviceToken> captor = ArgumentCaptor.forClass(DeviceToken.class);
        verify(deviceTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void deactivateToken_owner_deactivates() {
        DeviceToken token = new DeviceToken();
        token.setFcmToken("my-token");
        token.setUserId(userId);
        token.setActive(true);
        when(deviceTokenRepository.findByFcmToken("my-token")).thenReturn(Optional.of(token));

        deviceTokenService.deactivateToken(userId, "my-token");

        assertThat(token.getActive()).isFalse();
        verify(deviceTokenRepository).save(token);
    }

    @Test
    void deactivateToken_notOwner_throws403() {
        DeviceToken token = new DeviceToken();
        token.setFcmToken("my-token");
        token.setUserId(UUID.randomUUID());
        when(deviceTokenRepository.findByFcmToken("my-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> deviceTokenService.deactivateToken(userId, "my-token"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not belong to you");
    }

    @Test
    void deactivateToken_notFound_throws404() {
        when(deviceTokenRepository.findByFcmToken("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceTokenService.deactivateToken(userId, "missing"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void deactivateByToken_systemTriggered_noOwnershipCheckRequired() {
        DeviceToken token = new DeviceToken();
        token.setFcmToken("stale-token");
        token.setUserId(UUID.randomUUID());
        token.setActive(true);
        when(deviceTokenRepository.findByFcmToken("stale-token")).thenReturn(Optional.of(token));

        deviceTokenService.deactivateByToken("stale-token");

        assertThat(token.getActive()).isFalse();
    }

    @Test
    void deactivateByToken_unknownToken_isSilentNoOp() {
        when(deviceTokenRepository.findByFcmToken("unknown")).thenReturn(Optional.empty());

        deviceTokenService.deactivateByToken("unknown");

        verify(deviceTokenRepository, never()).save(any(DeviceToken.class));
    }
}
