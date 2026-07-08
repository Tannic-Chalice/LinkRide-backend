-- Phase 2A: Google Directions route generation. Existing rides never had a route attempted,
-- so they backfill to PENDING (Postgres applies DEFAULT to existing rows on ADD COLUMN).
ALTER TABLE rides ADD COLUMN route_generation_state TEXT NOT NULL DEFAULT 'PENDING';

CREATE INDEX idx_rides_route_generation_state ON rides (route_generation_state);
