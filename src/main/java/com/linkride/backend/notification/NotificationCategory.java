package com.linkride.backend.notification;

/**
 * Coarse, curated notification taxonomy (Phase 6 — see backend/docs/phase-6-notifications.md
 * §5.1/ADR-2). Rare and deliberate to add to, unlike {@link Notification#getType()}, which is a
 * free-form string precisely so new fine-grained event codes never need a migration.
 */
public enum NotificationCategory {
    BOOKING,
    RIDE,
    BOARDING,
    SYSTEM,
    COMMUNITY,
    SOCIAL
}
