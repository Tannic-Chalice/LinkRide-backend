-- Phase 3.1: booking domain foundation. Introduces the bookings table and widens
-- rides.status (previously always 'SCHEDULED') to the full ride lifecycle.

ALTER TABLE rides ADD CONSTRAINT chk_rides_status
    CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));

CREATE TABLE bookings (
    booking_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id          UUID NOT NULL REFERENCES rides(ride_id),
    passenger_id     UUID NOT NULL REFERENCES users(id),

    seats_requested  INT NOT NULL DEFAULT 1,

    pickup_lat       DOUBLE PRECISION NOT NULL,
    pickup_lng       DOUBLE PRECISION NOT NULL,
    pickup_label     TEXT,
    drop_lat         DOUBLE PRECISION NOT NULL,
    drop_lng         DOUBLE PRECISION NOT NULL,
    drop_label       TEXT,

    status           TEXT NOT NULL DEFAULT 'PENDING',

    decided_at       TIMESTAMPTZ,
    cancelled_at     TIMESTAMPTZ,
    cancelled_by     TEXT,

    version          INT NOT NULL DEFAULT 0,

    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_bookings_seats_positive CHECK (seats_requested > 0),
    CONSTRAINT chk_bookings_status CHECK (status IN
        ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'EXPIRED', 'COMPLETED'))
);

-- A passenger may have at most one *active* (still-live) request per ride.
-- Re-requesting after REJECTED/CANCELLED/EXPIRED is allowed, so the index only
-- covers PENDING/ACCEPTED. This is the authoritative duplicate-request guard —
-- the service layer's pre-check is a friendlier error message on top, not the
-- source of truth under concurrent requests.
CREATE UNIQUE INDEX uq_bookings_active_ride_passenger
    ON bookings (ride_id, passenger_id)
    WHERE status IN ('PENDING', 'ACCEPTED');

CREATE INDEX idx_bookings_ride_id ON bookings (ride_id);
CREATE INDEX idx_bookings_passenger_id ON bookings (passenger_id);
CREATE INDEX idx_bookings_status ON bookings (status);
