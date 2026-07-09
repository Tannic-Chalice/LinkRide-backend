package com.linkride.backend.route.geometry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoMathTest {

    @Test
    void haversineMeters_knownCityPair_matchesApproximateDistance() {
        // Bengaluru MG Road area -> Kempegowda International Airport, ~28km as the crow flies.
        double distance = GeoMath.haversineMeters(12.9716, 77.5946, 13.1986, 77.7066);

        assertThat(distance).isBetween(25_000.0, 32_000.0);
    }

    @Test
    void haversineMeters_samePoint_isZero() {
        assertThat(GeoMath.haversineMeters(12.9716, 77.5946, 12.9716, 77.5946)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void nearestPointOnSegment_perpendicularProjection_landsBetweenEndpoints() {
        // A short east-west segment; a point directly "above" its midpoint should project there.
        NearestPointOnSegment result = GeoMath.nearestPointOnSegment(
                12.9720, 77.6000,
                12.9716, 77.5990,
                12.9716, 77.6010);

        assertThat(result.fraction()).isCloseTo(0.5, within(0.05));
        assertThat(result.lat()).isCloseTo(12.9716, within(1e-4));
    }

    @Test
    void nearestPointOnSegment_pointBeforeSegmentStart_clampsToStart() {
        NearestPointOnSegment result = GeoMath.nearestPointOnSegment(
                12.9700, 77.5980,
                12.9716, 77.5990,
                12.9716, 77.6010);

        assertThat(result.fraction()).isEqualTo(0.0);
        assertThat(result.lat()).isCloseTo(12.9716, within(1e-9));
        assertThat(result.lng()).isCloseTo(77.5990, within(1e-9));
    }

    @Test
    void nearestPointOnSegment_pointAfterSegmentEnd_clampsToEnd() {
        NearestPointOnSegment result = GeoMath.nearestPointOnSegment(
                12.9700, 77.6030,
                12.9716, 77.5990,
                12.9716, 77.6010);

        assertThat(result.fraction()).isEqualTo(1.0);
        assertThat(result.lng()).isCloseTo(77.6010, within(1e-9));
    }
}
