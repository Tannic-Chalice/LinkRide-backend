package com.linkride.backend.tracking;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Driver-facing live progress snapshot (§8/§9) — the response to
 * {@code POST /rides/{rideId}/location} and {@code GET /rides/{rideId}/tracking}, and what's
 * pushed over WebSocket to the driver's own queue. Not persisted (§14); rebuilt fresh from
 * {@link LiveRideStateStore} plus a live {@link RouteProgressEngine}/{@link EtaEngine}
 * computation on every request.
 */
public record RideProgressSnapshot(
        UUID rideId,
        double lastLatitude,
        double lastLongitude,
        Instant lastUpdatedAt,
        double driverCumulativeDistanceMeters,
        double remainingDistanceMeters,
        double remainingDurationSeconds,
        boolean onRoute,
        List<WaypointProgress> waypoints,
        List<PassengerEtaView> passengerEtas) {
}
