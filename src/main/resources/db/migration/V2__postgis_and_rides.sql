-- Enables PostGIS and introduces the Ride module (Phase 1: driver ride creation).

CREATE EXTENSION IF NOT EXISTS postgis;

-- Vehicle.isActive: disambiguates which of a driver's (possibly several) vehicles
-- a new ride should be created against.
ALTER TABLE vehicles ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;

CREATE TABLE rides (
    ride_id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id                   UUID NOT NULL REFERENCES users(id),
    vehicle_id                  UUID NOT NULL REFERENCES vehicles(car_id),

    pickup_name                 TEXT NOT NULL,
    pickup_address               TEXT NOT NULL,
    pickup_point                 geography(Point,4326) NOT NULL,

    destination_name            TEXT NOT NULL,
    destination_address         TEXT NOT NULL,
    destination_point           geography(Point,4326) NOT NULL,

    departure_time               TIMESTAMPTZ NOT NULL,

    offered_seats                 INT NOT NULL,
    total_seats                   INT NOT NULL,
    available_seats               INT NOT NULL,

    repeat_schedule               JSONB,

    quiet_ride                    BOOLEAN,
    ac_preference                 BOOLEAN,
    trunk_space                   BOOLEAN,

    route_polyline                 TEXT,
    estimated_distance_meters      INT,
    estimated_duration_seconds     INT,

    status                         TEXT NOT NULL DEFAULT 'SCHEDULED',

    created_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ride_waypoints (
    waypoint_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id         UUID NOT NULL REFERENCES rides(ride_id) ON DELETE CASCADE,
    sequence        INT NOT NULL,
    name            TEXT NOT NULL,
    address         TEXT NOT NULL,
    point           geography(Point,4326) NOT NULL
);

CREATE INDEX idx_rides_pickup_point ON rides USING GIST (pickup_point);
CREATE INDEX idx_rides_destination_point ON rides USING GIST (destination_point);
CREATE INDEX idx_rides_driver_id ON rides (driver_id);
CREATE INDEX idx_rides_status ON rides (status);
CREATE INDEX idx_rides_departure_time ON rides (departure_time);
CREATE INDEX idx_ride_waypoints_ride_id ON ride_waypoints (ride_id);
