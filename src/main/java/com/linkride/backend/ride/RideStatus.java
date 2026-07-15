package com.linkride.backend.ride;

/**
 * Ride lifecycle (Phase 3). Transitions are always explicit driver actions —
 * {@code RideService.startRide/completeRide/cancelRide} — never inferred from
 * {@code departureTime} passing. There is no scheduler in this codebase that
 * flips status by time, and none is planned; a ride whose departure time has
 * quietly passed without the driver calling {@code start()} simply stays
 * {@code SCHEDULED} until the driver acts.
 */
public enum RideStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
