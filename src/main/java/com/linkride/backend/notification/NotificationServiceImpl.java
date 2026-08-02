package com.linkride.backend.notification;

import com.linkride.backend.notification.FcmPushSender.PushOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final DeviceTokenService deviceTokenService;
    private final FcmPushSender fcmPushSender;
    private final NotificationProperties properties;

    @Override
    @Transactional
    public void createAndDeliver(NotificationEvent event) {
        Notification notification = new Notification();
        notification.setRecipientUserId(event.recipientUserId());
        notification.setCategory(event.category());
        notification.setType(event.type());
        notification.setTitle(event.title());
        notification.setBody(event.body());
        notification.setRelatedEntityType(event.relatedEntityType());
        notification.setRelatedEntityId(event.relatedEntityId());

        notification = notificationRepository.save(notification);

        try {
            deliver(notification, event);
        } catch (Exception e) {
            // The row above is already committed-in-progress -- never leave it stuck at PENDING,
            // and never let a delivery-side failure escape this method (architecture doc §4/ADR-3).
            log.error("Notification delivery failed unexpectedly for notification {}: {}",
                    notification.getNotificationId(), e.getMessage(), e);
            notification.setDeliveryStatus(DeliveryStatus.FAILED);
            notificationRepository.save(notification);
        }
    }

    private void deliver(Notification notification, NotificationEvent event) {
        boolean pushEnabled = preferenceRepository
                .findByUserIdAndCategory(event.recipientUserId(), event.category())
                .map(NotificationPreference::getPushEnabled)
                .orElse(true);

        if (!pushEnabled) {
            notification.setDeliveryStatus(DeliveryStatus.SKIPPED);
            notificationRepository.save(notification);
            return;
        }

        List<DeviceToken> tokens = deviceTokenRepository.findByUserIdAndActiveTrue(event.recipientUserId());
        if (tokens.isEmpty()) {
            notification.setDeliveryStatus(DeliveryStatus.SKIPPED);
            notificationRepository.save(notification);
            return;
        }

        Map<String, String> data = buildDataPayload(notification);

        boolean anySent = false;
        for (DeviceToken token : tokens) {
            PushOutcome outcome = fcmPushSender.send(token.getFcmToken(), notification.getTitle(), notification.getBody(), data);
            if (outcome == PushOutcome.SENT) {
                anySent = true;
            } else if (outcome == PushOutcome.TOKEN_INVALID) {
                deviceTokenService.deactivateByToken(token.getFcmToken());
            }
        }

        notification.setDeliveryStatus(anySent ? DeliveryStatus.SENT : DeliveryStatus.FAILED);
        notificationRepository.save(notification);
    }

    private Map<String, String> buildDataPayload(Notification notification) {
        Map<String, String> data = new HashMap<>();
        data.put("notificationId", notification.getNotificationId().toString());
        data.put("type", notification.getType());
        if (notification.getRelatedEntityType() != null) {
            data.put("relatedEntityType", notification.getRelatedEntityType());
        }
        if (notification.getRelatedEntityId() != null) {
            data.put("relatedEntityId", notification.getRelatedEntityId().toString());
        }
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPageResponse listNotifications(UUID recipientUserId, NotificationCategory category,
                                                        NotificationStatus status, int page, int size) {
        int cappedSize = Math.min(size > 0 ? size : properties.getInboxDefaultPageSize(), properties.getInboxMaxPageSize());
        Page<Notification> result = notificationRepository.findInbox(
                recipientUserId, category, status, PageRequest.of(Math.max(page, 0), cappedSize));
        return NotificationPageResponse.from(result);
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(UUID recipientUserId, NotificationCategory category) {
        long count = category != null
                ? notificationRepository.countByRecipientUserIdAndStatusAndCategory(recipientUserId, NotificationStatus.UNREAD, category)
                : notificationRepository.countByRecipientUserIdAndStatus(recipientUserId, NotificationStatus.UNREAD);
        return UnreadCountResponse.builder().count(count).build();
    }

    @Override
    @Transactional
    public NotificationResponse markRead(UUID recipientUserId, UUID notificationId) {
        Notification notification = findOwnedNotification(recipientUserId, notificationId);

        if (notification.getStatus() == NotificationStatus.UNREAD) {
            notification.setStatus(NotificationStatus.READ);
            notification.setReadAt(OffsetDateTime.now());
            notification = notificationRepository.save(notification);
        }

        return NotificationResponse.from(notification);
    }

    @Override
    @Transactional
    public void markAllRead(UUID recipientUserId, NotificationCategory category) {
        notificationRepository.markAllRead(recipientUserId, category);
    }

    @Override
    @Transactional
    public void archive(UUID recipientUserId, UUID notificationId) {
        Notification notification = findOwnedNotification(recipientUserId, notificationId);
        notification.setStatus(NotificationStatus.ARCHIVED);
        notification.setArchivedAt(OffsetDateTime.now());
        notificationRepository.save(notification);
    }

    private Notification findOwnedNotification(UUID recipientUserId, UUID notificationId) {
        return notificationRepository.findByNotificationIdAndRecipientUserId(notificationId, recipientUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationPreferenceDto> getPreferences(UUID userId) {
        Map<NotificationCategory, NotificationPreference> stored = new HashMap<>();
        preferenceRepository.findByUserId(userId).forEach(p -> stored.put(p.getCategory(), p));

        return Arrays.stream(NotificationCategory.values())
                .map(category -> stored.containsKey(category)
                        ? NotificationPreferenceDto.from(stored.get(category))
                        : NotificationPreferenceDto.defaultFor(category))
                .toList();
    }

    @Override
    @Transactional
    public List<NotificationPreferenceDto> updatePreferences(UUID userId, List<NotificationPreferenceDto> updates) {
        for (NotificationPreferenceDto update : updates) {
            NotificationPreference preference = preferenceRepository
                    .findByUserIdAndCategory(userId, update.getCategory())
                    .orElseGet(() -> {
                        NotificationPreference created = new NotificationPreference();
                        created.setUserId(userId);
                        created.setCategory(update.getCategory());
                        return created;
                    });
            preference.setPushEnabled(update.getPushEnabled());
            preferenceRepository.save(preference);
        }

        return getPreferences(userId);
    }
}
