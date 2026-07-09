package com.linkride.backend.route.geometry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PolylineCodecTest {

    @Test
    void decode_googleDocumentedExample_producesExpectedPoints() {
        // https://developers.google.com/maps/documentation/utilities/polylinealgorithm — the
        // canonical worked example from Google's own spec.
        List<LatLng> points = PolylineCodec.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@");

        assertThat(points).hasSize(3);
        assertThat(points.get(0).lat()).isCloseTo(38.5, within(1e-5));
        assertThat(points.get(0).lng()).isCloseTo(-120.2, within(1e-5));
        assertThat(points.get(1).lat()).isCloseTo(40.7, within(1e-5));
        assertThat(points.get(1).lng()).isCloseTo(-120.95, within(1e-5));
        assertThat(points.get(2).lat()).isCloseTo(43.252, within(1e-5));
        assertThat(points.get(2).lng()).isCloseTo(-126.453, within(1e-5));
    }

    @Test
    void decode_nullOrEmpty_returnsEmptyList() {
        assertThat(PolylineCodec.decode(null)).isEmpty();
        assertThat(PolylineCodec.decode("")).isEmpty();
    }
}
