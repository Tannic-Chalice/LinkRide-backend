package com.linkride.backend.tracking;

/**
 * Result of running an inbound GPS fix through {@link GpsFixValidator} (§7.2 of
 * backend/docs/phase-7-live-trip-management.md). Coordinate bounds never surface here — those
 * are rejected by Bean Validation on {@link LocationUpdateRequest} itself, before the
 * controller method body (and therefore this pipeline) ever runs.
 */
public enum GpsValidationOutcome {
    ACCEPTED,
    IGNORED_DUPLICATE,
    REJECTED_STALE_TIMESTAMP,
    REJECTED_FUTURE_TIMESTAMP,
    REJECTED_IMPOSSIBLE_JUMP
}
