package com.linkride.backend.route.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Attaches cumulative distance/duration to each raw decoded point, interpolating the provider's
 * total distance/duration proportionally to each point's haversine distance along the route
 * (design doc §2.1 step 3), and computes the route's {@link BoundingBox} in the same single pass
 * over the decoded points. Route simplification (Douglas-Peucker, §1.3) is intentionally not
 * performed here — deferred past V1 per §2's own guidance, since brute-force scanning against
 * the raw decoded point list is microseconds of work at current ride volumes.
 */
public final class CumulativeMetricsCalculator {

    private CumulativeMetricsCalculator() {
    }

    public static RouteGeometry annotate(List<LatLng> decodedPoints, double totalDistanceMeters, double totalDurationSeconds) {
        if (decodedPoints.isEmpty()) {
            return new RouteGeometry(List.of(), 0, 0, BoundingBox.of(decodedPoints));
        }

        double[] segmentDistances = new double[decodedPoints.size() - 1];
        double rawTotalDistance = 0;
        for (int i = 0; i < segmentDistances.length; i++) {
            LatLng a = decodedPoints.get(i);
            LatLng b = decodedPoints.get(i + 1);
            segmentDistances[i] = GeoMath.haversineMeters(a.lat(), a.lng(), b.lat(), b.lng());
            rawTotalDistance += segmentDistances[i];
        }

        List<RoutePoint> points = new ArrayList<>(decodedPoints.size());
        double cumulativeRawDistance = 0;
        for (int i = 0; i < decodedPoints.size(); i++) {
            LatLng latLng = decodedPoints.get(i);
            double proportion = rawTotalDistance == 0 ? 0 : cumulativeRawDistance / rawTotalDistance;

            points.add(new RoutePoint(
                    latLng.lat(),
                    latLng.lng(),
                    proportion * totalDistanceMeters,
                    proportion * totalDurationSeconds,
                    i));

            if (i < segmentDistances.length) {
                cumulativeRawDistance += segmentDistances[i];
            }
        }

        return new RouteGeometry(points, totalDistanceMeters, totalDurationSeconds, BoundingBox.of(decodedPoints));
    }
}
