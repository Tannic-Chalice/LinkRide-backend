package com.linkride.backend.route.geometry;

import java.util.List;

/**
 * A decoded route as an ordered, cumulative-distance/time-annotated sequence of points (design
 * doc §1.1) — a parameterized curve, not just a shape. A record (rather than a plain class) so
 * it round-trips through Jackson/JSONB the same way {@code Ride.repeatSchedule} already does,
 * with no custom (de)serialization needed.
 *
 * <p>{@code boundingBox} is a first-class, precomputed property (built once by
 * {@link CumulativeMetricsCalculator} as part of {@link RouteGeometryBuilder}'s single decode
 * pass) rather than something callers derive on demand — corridor computation (Phase 2B.5) uses
 * it as a mandatory, cheap pre-check before touching per-point geometry at all.</p>
 */
public record RouteGeometry(
        List<RoutePoint> points,
        double totalDistanceMeters,
        double totalDurationSeconds,
        BoundingBox boundingBox) {

    /**
     * Nearest point on this route to the given coordinate — a point-to-*polyline* query (checks
     * every segment), not point-to-vertex, per design doc §5.1.
     */
    public NearestPointOnRoute nearestPoint(double lat, double lng) {
        if (points.isEmpty()) {
            return new NearestPointOnRoute(lat, lng, 0, Double.MAX_VALUE);
        }
        if (points.size() == 1) {
            RoutePoint only = points.get(0);
            double distance = GeoMath.haversineMeters(lat, lng, only.lat(), only.lng());
            return new NearestPointOnRoute(only.lat(), only.lng(), only.cumulativeDistanceMeters(), distance);
        }

        RoutePoint bestSegmentStart = null;
        NearestPointOnSegment best = null;

        for (int i = 0; i < points.size() - 1; i++) {
            RoutePoint start = points.get(i);
            RoutePoint end = points.get(i + 1);

            NearestPointOnSegment candidate = GeoMath.nearestPointOnSegment(
                    lat, lng, start.lat(), start.lng(), end.lat(), end.lng());

            if (best == null || candidate.distanceMeters() < best.distanceMeters()) {
                best = candidate;
                bestSegmentStart = start;
            }
        }

        double distanceIntoSegment = GeoMath.haversineMeters(
                bestSegmentStart.lat(), bestSegmentStart.lng(), best.lat(), best.lng());
        double cumulativeDistanceMeters = bestSegmentStart.cumulativeDistanceMeters() + distanceIntoSegment;

        return new NearestPointOnRoute(best.lat(), best.lng(), cumulativeDistanceMeters, best.distanceMeters());
    }
}
