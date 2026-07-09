package com.linkride.backend.route.geometry;

/**
 * A point on a route, augmented with progress along the route (design doc §1.1) — not just
 * where it is, but how far into the trip it is. This is what turns a route from "a shape" into
 * a parameterized curve: pickup/drop feasibility is temporal as well as geometric.
 */
public record RoutePoint(
        double lat,
        double lng,
        double cumulativeDistanceMeters,
        double cumulativeDurationSeconds,
        int segmentIndex) {
}
