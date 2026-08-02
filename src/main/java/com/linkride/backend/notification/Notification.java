package com.linkride.backend.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A durable, queryable record of one fact told to one user (Phase 6 — see
 * backend/docs/phase-6-notifications.md §5.1). This row, not the push it may or may not have
 * triggered, is the source of truth: {@link #deliveryStatus} tracks the FCM side independently
 * of {@link #status}, the in-app read state, and never gates it.
 *
 * <p>{@code recipientUserId} is a plain UUID, not a {@code @ManyToOne} to {@link
 * com.linkride.backend.entity.User} — one-directional relations only, matching the rest of this
 * codebase (see {@code Booking}'s javadoc): {@code User} never holds a back-collection of its
 * notifications, fetched via {@link NotificationRepository} instead.</p>
 *
 * <p>{@code relatedEntityType}/{@code relatedEntityId} is a polymorphic, FK-less deep-link
 * pointer (e.g. {@code ("BOOKING", bookingId)}) — a single FK column can't reference multiple
 * tables (ADR-8).</p>
 */
@Data
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "notification_id", updatable = false, nullable = false)
    private UUID notificationId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private NotificationCategory category;

    /**
     * Fine-grained event code (e.g. {@code BOOKING_ACCEPTED}) — deliberately a plain string, not
     * a Java enum. New event types are introduced by nearly every future feature phase; making
     * this a string means none of them ever need a schema/enum migration (ADR-2).
     */
    @Column(name = "type", nullable = false)
    private String type;

    /**
     * Rendered at write time and stored verbatim — never re-templated at read time (ADR-7), so a
     * later change to a template can't silently rewrite the meaning of historical notifications.
     */
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "related_entity_type")
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private UUID relatedEntityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NotificationStatus status = NotificationStatus.UNREAD;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false)
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
