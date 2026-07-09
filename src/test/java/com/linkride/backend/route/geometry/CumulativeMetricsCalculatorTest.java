package com.linkride.backend.route.geometry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CumulativeMetricsCalculatorTest {

    @Test
    void annotate_threeEquallySpacedPoints_interpolatesProportionally() {
        List<LatLng> points = List.of(
                new LatLng(12.9716, 77.5990),
                new LatLng(12.9716, 77.6010),
                new LatLng(12.9716, 77.6030));

        RouteGeometry geometry = CumulativeMetricsCalculator.annotate(points, 1000, 200);

        assertThat(geometry.points()).hasSize(3);
        assertThat(geometry.points().get(0).cumulativeDistanceMeters()).isCloseTo(0.0, within(1e-6));
        assertThat(geometry.points().get(0).cumulativeDurationSeconds()).isCloseTo(0.0, within(1e-6));
        // Roughly equal spacing (not exact, since haversine segment lengths differ marginally) —
        // the middle point should land close to the halfway mark of both totals.
        assertThat(geometry.points().get(1).cumulativeDistanceMeters()).isCloseTo(500.0, within(50.0));
        assertThat(geometry.points().get(2).cumulativeDistanceMeters()).isCloseTo(1000.0, within(1.0));
        assertThat(geometry.points().get(2).cumulativeDurationSeconds()).isCloseTo(200.0, within(1.0));
        assertThat(geometry.totalDistanceMeters()).isEqualTo(1000);
        assertThat(geometry.totalDurationSeconds()).isEqualTo(200);
        assertThat(geometry.boundingBox().minLat()).isEqualTo(12.9716);
        assertThat(geometry.boundingBox().maxLng()).isEqualTo(77.6030);
    }

    @Test
    void annotate_emptyInput_returnsEmptyGeometry() {
        RouteGeometry geometry = CumulativeMetricsCalculator.annotate(List.of(), 0, 0);

        assertThat(geometry.points()).isEmpty();
    }

    @Test
    void annotate_singlePoint_hasZeroCumulativeValues() {
        RouteGeometry geometry = CumulativeMetricsCalculator.annotate(
                List.of(new LatLng(12.9716, 77.5990)), 0, 0);

        assertThat(geometry.points()).hasSize(1);
        assertThat(geometry.points().get(0).cumulativeDistanceMeters()).isEqualTo(0.0);
    }
}
