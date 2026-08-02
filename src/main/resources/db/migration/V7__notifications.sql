-- Phase 6: notification domain — durable in-app history, device token registry, per-category
-- push preferences (backend/docs/phase-6-notifications.md). Additive only; no existing table
-- gains a column (Phase 5's "additive, not disruptive" principle, applied here).

CREATE TABLE notifications (
    notification_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_user_id    UUID NOT NULL REFERENCES users(id),

    category              TEXT NOT NULL CHECK (category IN
                              ('BOOKING', 'RIDE', 'BOARDING', 'SYSTEM', 'COMMUNITY', 'SOCIAL')),
    -- Fine-grained event code (e.g. BOOKING_ACCEPTED). Deliberately NOT CHECK-constrained, unlike
    -- category: new types must never require a migration (architecture doc §5.1 / ADR-2).
    type                  TEXT NOT NULL,
    title                 TEXT NOT NULL,
    body                  TEXT NOT NULL,

    -- Polymorphic deep-link target; no FK -- may point at bookings, rides, or a future table
    -- (ADR-8). related_entity_type is a plain tag, not CHECK-constrained, for the same reason
    -- type isn't: a future entity type shouldn't need a migration either.
    related_entity_type  TEXT,
    related_entity_id    UUID,

    status                TEXT NOT NULL DEFAULT 'UNREAD' CHECK (status IN ('UNREAD', 'READ', 'ARCHIVED')),
    read_at               TIMESTAMPTZ,
    archived_at           TIMESTAMPTZ,

    delivery_status       TEXT NOT NULL DEFAULT 'PENDING' CHECK (delivery_status IN
                              ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),

    version               INT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The inbox query: unread-first, newest-first, per recipient.
CREATE INDEX idx_notifications_recipient_status_created
    ON notifications (recipient_user_id, status, created_at DESC);

-- Category-filtered fetch.
CREATE INDEX idx_notifications_recipient_category
    ON notifications (recipient_user_id, category);

-- One row per (user, physical device/install). A device can only ever hold one current FCM
-- token -- registration upserts on fcm_token, not (user_id, platform), since FCM itself rotates
-- tokens independently of any LinkRide action (see architecture doc §9).
CREATE TABLE device_tokens (
    device_token_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id),
    fcm_token        TEXT NOT NULL,
    platform         TEXT NOT NULL CHECK (platform IN ('ANDROID', 'IOS', 'WEB')),
    active           BOOLEAN NOT NULL DEFAULT true,
    last_seen_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_device_tokens_fcm_token ON device_tokens (fcm_token);

-- Push fan-out query: every active token for a recipient.
CREATE INDEX idx_device_tokens_user_active ON device_tokens (user_id, active);

-- One row per (user, category). Push-only gate -- in-app history is never suppressed by
-- preference (architecture doc §10/ADR-4). Absent row means "defaults apply" (push enabled), so
-- no backfill is ever needed for existing users or future categories.
CREATE TABLE notification_preferences (
    preference_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users(id),
    category       TEXT NOT NULL CHECK (category IN
                       ('BOOKING', 'RIDE', 'BOARDING', 'SYSTEM', 'COMMUNITY', 'SOCIAL')),
    push_enabled   BOOLEAN NOT NULL DEFAULT true,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_notification_preferences_user_category
    ON notification_preferences (user_id, category);
