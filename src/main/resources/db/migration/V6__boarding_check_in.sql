-- Phase 4.1: boarding domain foundation. Adds CHECKED_IN/NO_SHOW to the booking lifecycle and
-- introduces boarding_tokens (QR) and boarding_verifications (OTP + check-in record).

ALTER TABLE bookings DROP CONSTRAINT chk_bookings_status;
ALTER TABLE bookings ADD CONSTRAINT chk_bookings_status CHECK (status IN
    ('PENDING', 'ACCEPTED', 'CHECKED_IN', 'REJECTED', 'CANCELLED', 'EXPIRED', 'NO_SHOW', 'COMPLETED'));

-- A short-lived, single-use QR credential scoped to exactly one booking, not a ride — the driver
-- explicitly selects a passenger from the boarding screen and a token is issued for that booking
-- alone. booking_id alone determines ride, passenger, and driver, so none of those are duplicated
-- here.
CREATE TABLE boarding_tokens (
    token_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token        TEXT NOT NULL UNIQUE,
    booking_id   UUID NOT NULL REFERENCES bookings(booking_id),
    created_by   UUID NOT NULL REFERENCES users(id),

    issued_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    consumed_at  TIMESTAMPTZ
);

CREATE INDEX idx_boarding_tokens_booking_id ON boarding_tokens (booking_id);

-- Enforces "at most one live token per booking" at the database level, not just in the service
-- layer's find-or-create logic — closes the race where two near-simultaneous QR-issue requests
-- both observe "no active token" and both insert one. The predicate is immutable (no now()), so
-- it only needs revoked_at/consumed_at, not expires_at: generation logic must explicitly revoke a
-- stale (time-expired but not yet revoked) token before issuing a new one, which keeps this
-- invariant true regardless of expiry.
CREATE UNIQUE INDEX uq_boarding_tokens_active_per_booking
    ON boarding_tokens (booking_id)
    WHERE revoked_at IS NULL AND consumed_at IS NULL;

-- Boarding-verification state for exactly one booking. Deliberately not columns on bookings
-- itself (see Booking's javadoc) — OTP/QR verification is transient authentication data, not
-- booking state. One row per booking, created lazily on the first boarding action (QR issue or
-- OTP generation), not eagerly when the booking is accepted.
CREATE TABLE boarding_verifications (
    verification_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id        UUID NOT NULL UNIQUE REFERENCES bookings(booking_id),

    otp_hash          TEXT,
    otp_expires_at    TIMESTAMPTZ,
    otp_attempts      INT NOT NULL DEFAULT 0,

    checked_in_at     TIMESTAMPTZ,
    checked_in_via    TEXT CHECK (checked_in_via IN ('QR', 'OTP')),

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_boarding_verifications_booking_id ON boarding_verifications (booking_id);
