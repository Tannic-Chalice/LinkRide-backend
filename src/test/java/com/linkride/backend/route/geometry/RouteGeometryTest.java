package com.linkride.backend.route.geometry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RouteGeometryTest {

    private RouteGeometry routeGeometry(List<RoutePoint> points, double totalDistanceMeters, double totalDurationSeconds) {
        List<LatLng> latLngs = points.stream().map(p -> new LatLng(p.lat(), p.lng())).toList();
        return new RouteGeometry(points, totalDistanceMeters, totalDurationSeconds, BoundingBox.of(latLngs));
    }

    @Test
    void nearestPoint_pointOnSecondSegment_picksThatSegmentNotTheFirst() {
        double p0Lat = 12.9716, p0Lng = 77.5990;
        double p1Lat = 12.9716, p1Lng = 77.6010;
        double p2Lat = 12.9716, p2Lng = 77.6030;

        double d1 = GeoMath.haversineMeters(p0Lat, p0Lng, p1Lat, p1Lng);
        double d2 = GeoMath.haversineMeters(p1Lat, p1Lng, p2Lat, p2Lng);

        RouteGeometry route = routeGeometry(List.of(
                new RoutePoint(p0Lat, p0Lng, 0, 0, 0),
                new RoutePoint(p1Lat, p1Lng, d1, 0, 1),
                new RoutePoint(p2Lat, p2Lng, d1 + d2, 0, 2)),
                d1 + d2, 0);

        // Exactly on-line, midway through the second segment.
        var result = route.nearestPoint(12.9716, 77.6020);

        assertThat(result.distanceMeters()).isCloseTo(0.0, within(1.0));
        assertThat(result.cumulativeDistanceMeters()).isCloseTo(d1 + d2 / 2, within(1.0));
    }

    @Test
    void nearestPoint_offToOneSide_reportsPositiveWalkDistance() {
        RouteGeometry route = routeGeometry(List.of(
                new RoutePoint(12.9716, 77.5990, 0, 0, 0),
                new RoutePoint(12.9716, 77.6010, 100, 0, 1)),
                100, 0);

        // ~0.009 degrees north of the line, roughly 1000m off.
        var result = route.nearestPoint(12.9806, 77.6000);

        assertThat(result.distanceMeters()).isGreaterThan(500);
    }

    @Test
    void nearestPoint_singlePointRoute_fallsBackToPointToPointDistance() {
        RouteGeometry route = routeGeometry(List.of(new RoutePoint(12.9716, 77.5990, 0, 0, 0)), 0, 0);

        var result = route.nearestPoint(12.9716, 77.5990);

        assertThat(result.distanceMeters()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void boundingBox_isComputedFromPoints() {
        RouteGeometry route = routeGeometry(List.of(
                new RoutePoint(12.90, 77.50, 0, 0, 0),
                new RoutePoint(13.00, 77.60, 100, 0, 1)),
                100, 0);

        assertThat(route.boundingBox().minLat()).isEqualTo(12.90);
        assertThat(route.boundingBox().maxLat()).isEqualTo(13.00);
        assertThat(route.boundingBox().minLng()).isEqualTo(77.50);
        assertThat(route.boundingBox().maxLng()).isEqualTo(77.60);
    }
}
