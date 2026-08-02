package com.linkride.backend.notification;

import java.util.UUID;

/**
 * The one event type every producer publishes (Phase 6 — see
 * backend/docs/phase-6-notifications.md §7). A single generic record, not a class per business
 * fact ({@code BookingAcceptedEvent}, {@code RideStartedEvent}, ...): {@code type} is a free-form
 * string precisely so a new fact never needs a new Java type, matching {@link
 * Notification#getType()}'s own reasoning (ADR-2).
 *
 * <p>Published via a plain {@link org.springframework.context.ApplicationEventPublisher} —
 * consumed exactly once, by {@link NotificationEventListener}, only after the publishing
 * transaction commits ({@code @TransactionalEventListener(phase = AFTER_COMMIT)}). Producers in
 * {@code booking}/{@code ride}/{@code boarding} never call {@code NotificationService} directly —
 * they publish this and walk away.</p>
 */
public record NotificationEvent(
        UUID recipientUserId,
        NotificationCategory category,
        String type,
        String title,
        String body,
        String relatedEntityType,
        UUID relatedEntityId) {
}
