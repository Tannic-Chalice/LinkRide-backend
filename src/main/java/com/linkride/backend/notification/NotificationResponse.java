package com.linkride.backend.notification;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class NotificationResponse {

    private UUID notificationId;
    private NotificationCategory category;
    private String type;
    private String title;
    private String body;
    private String relatedEntityType;
    private UUID relatedEntityId;
    private NotificationStatus status;
    private DeliveryStatus deliveryStatus;
    private OffsetDateTime createdAt;
    private OffsetDateTime readAt;

    public static NotificationResponse from(Notification notification) {
        return NotificationResponse.builder()
                .notificationId(notification.getNotificationId())
                .category(notification.getCategory())
                .type(notification.getType())
                .title(notification.getTitle())
                .body(notification.getBody())
                .relatedEntityType(notification.getRelatedEntityType())
                .relatedEntityId(notification.getRelatedEntityId())
                .status(notification.getStatus())
                .deliveryStatus(notification.getDeliveryStatus())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .build();
    }
}
