package com.linkride.backend.notification;

/**
 * Outcome of attempting to push-deliver a {@link Notification} (Phase 6). Never gates the
 * notification's own visibility — the backend is the source of truth regardless of whether the
 * push was ever sent (architecture doc §6/ADR-3).
 */
public enum DeliveryStatus {
    PENDING,
    SENT,
    FAILED,
    /** No push attempted at all: the recipient disabled push for this category, or has no active device token. */
    SKIPPED
}
