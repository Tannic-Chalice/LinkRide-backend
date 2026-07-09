package com.linkride.backend.route.geometry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteGeometryBuilderTest {

    private final RouteGeometryBuilder builder = new RouteGeometryBuilder();

    @Test
    void build_decodesAndAnnotatesInOneStep() {
        RouteGeometry geometry = builder.build("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5000, 600);

        assertThat(geometry.points()).hasSize(3);
        assertThat(geometry.points().get(0).cumulativeDistanceMeters()).isEqualTo(0.0);
        assertThat(geometry.totalDistanceMeters()).isEqualTo(5000);
        assertThat(geometry.totalDurationSeconds()).isEqualTo(600);
    }
}
