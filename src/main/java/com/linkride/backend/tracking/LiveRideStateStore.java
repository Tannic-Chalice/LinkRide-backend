package com.linkride.backend.tracking;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Port over live-ride-state storage (see backend/docs/phase-7-live-trip-management.md §6.2).
 * The in-memory adapter is the only V1 implementation; a Redis-backed adapter can replace it
 * later with no change to any caller — the same port/adapter pattern
 * {@link com.linkride.backend.route.RouteProvider} already established in this codebase.
 */
public interface LiveRideStateStore {

    Optional<LiveRideState> get(UUID rideId);

    void save(UUID rideId, LiveRideState state);

    void remove(UUID rideId);

    /**
     * Rides whose last known fix is older than {@code idleThreshold} — the safety net
     * {@code StaleLiveStateReaper} sweeps on, for a ride that never reaches an explicit
     * terminal call (crashed driver app, force-quit).
     */
    Collection<UUID> staleRideIds(Duration idleThreshold);
}
