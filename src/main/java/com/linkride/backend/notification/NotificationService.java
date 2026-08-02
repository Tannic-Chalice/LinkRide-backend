package com.linkride.backend.notification;

import java.util.List;
import java.util.UUID;

public interface NotificationService {

    /**
     * The single entry point {@link NotificationEventListener} calls after a producing
     * transaction commits: persists the notification, resolves the recipient's preference, and
     * fans out to every active device token if push is enabled. Never throws — a delivery
     * failure must never propagate back out of the async listener (architecture doc §4/ADR-3).
     */
    void createAndDeliver(NotificationEvent event);

    NotificationPageResponse listNotifications(UUID recipientUserId, NotificationCategory category,
                                                NotificationStatus status, int page, int size);

    UnreadCountResponse getUnreadCount(UUID recipientUserId, NotificationCategory category);

    /** Idempotent — marking an already-{@code READ} notification read again is a no-op success. */
    NotificationResponse markRead(UUID recipientUserId, UUID notificationId);

    void markAllRead(UUID recipientUserId, NotificationCategory category);

    /** Soft-archives — never a hard delete (architecture doc §8). */
    void archive(UUID recipientUserId, UUID notificationId);

    /** One entry per category, defaults filled in for any category with no stored preference row. */
    List<NotificationPreferenceDto> getPreferences(UUID userId);

    List<NotificationPreferenceDto> updatePreferences(UUID userId, List<NotificationPreferenceDto> updates);
}
