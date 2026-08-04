package com.linkride.backend.tracking;

import java.util.List;

/**
 * Output of {@link RouteProgressEngine} (§8) — the driver's projected position along the route
 * plus derived progress figures. Not persisted; consumed by {@link EtaEngine} and folded into
 * {@link RideProgressSnapshot}.
 */
public record RouteProgress(
        double driverCumulativeDistanceMeters,
        double remainingDistanceMeters,
        double remainingDurationSeconds,
        double distanceFromRouteMeters,
        boolean onRoute,
        List<WaypointProgress> waypoints) {
}
