-- Phase 2B.4: decoded, cumulative-distance/time-annotated route geometry, computed once at ride
-- creation (design doc §2.1) and reused by every passenger search instead of being redecoded
-- per-search. Nullable: only populated when route_generation_state = 'READY'; existing rides and
-- rides whose routing failed simply have no cached geometry yet.
ALTER TABLE rides ADD COLUMN route_geometry jsonb;
