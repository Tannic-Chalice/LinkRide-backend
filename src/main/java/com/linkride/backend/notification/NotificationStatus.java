package com.linkride.backend.notification;

/** In-app read state of a {@link Notification} (Phase 6). Independent of {@link DeliveryStatus} —
 * a notification can be {@code UNREAD} regardless of whether its push ever sent. */
public enum NotificationStatus {
    UNREAD,
    READ,
    ARCHIVED
}
