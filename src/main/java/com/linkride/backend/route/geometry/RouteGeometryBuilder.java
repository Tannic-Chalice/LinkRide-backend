package com.linkride.backend.route.geometry;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Facade composing decode + cumulative-metric annotation into a single {@link RouteGeometry}.
 * Route simplification (Douglas-Peucker, design doc §1.3) is intentionally not part of this
 * pipeline — deferred past V1 per design doc §2's own guidance; build it when search latency
 * data shows candidate-scanning is the bottleneck, not before.
 */
@Component
public class RouteGeometryBuilder {

    public RouteGeometry build(String encodedPolyline, double totalDistanceMeters, double totalDurationSeconds) {
        List<LatLng> decodedPoints = PolylineCodec.decode(encodedPolyline);
        return CumulativeMetricsCalculator.annotate(decodedPoints, totalDistanceMeters, totalDurationSeconds);
    }
}
