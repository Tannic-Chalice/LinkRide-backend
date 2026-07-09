package com.linkride.backend.discovery;

import com.linkride.backend.route.RouteFailureReason;
import com.linkride.backend.route.RouteGenerationState;
import com.linkride.backend.route.RouteResult;
import com.linkride.backend.route.geometry.RouteGeometryBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PassengerRouteMapperTest {

    // Real builder: it's pure decode/annotate logic, no dependencies — mocking it would just add noise.
    private final PassengerRouteMapper mapper = new PassengerRouteMapper(new RouteGeometryBuilder());

    @Test
    void toPassengerRoute_ready_copiesPolylineDistanceAndDurationAndDecodesGeometry() {
        // Google's own documented example polyline.
        PassengerRoute result = mapper.toPassengerRoute(RouteResult.ready("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5000, 600));

        assertThat(result.getRouteGenerationState()).isEqualTo(RouteGenerationState.READY);
        assertThat(result.getPolyline()).isEqualTo("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
        assertThat(result.getDistanceMeters()).isEqualTo(5000);
        assertThat(result.getDurationSeconds()).isEqualTo(600);
        assertThat(result.getGeometry()).isNotNull();
        assertThat(result.getGeometry().points()).hasSize(3);
    }

    @Test
    void toPassengerRoute_pending_leavesRouteFieldsAndGeometryNull() {
        PassengerRoute result = mapper.toPassengerRoute(RouteResult.pending(null));

        assertThat(result.getRouteGenerationState()).isEqualTo(RouteGenerationState.PENDING);
        assertThat(result.getPolyline()).isNull();
        assertThat(result.getDistanceMeters()).isNull();
        assertThat(result.getDurationSeconds()).isNull();
        assertThat(result.getGeometry()).isNull();
    }

    @Test
    void toPassengerRoute_unroutable_leavesRouteFieldsAndGeometryNull() {
        PassengerRoute result = mapper.toPassengerRoute(RouteResult.unroutable(RouteFailureReason.ZERO_RESULTS));

        assertThat(result.getRouteGenerationState()).isEqualTo(RouteGenerationState.UNROUTABLE);
        assertThat(result.getPolyline()).isNull();
        assertThat(result.getGeometry()).isNull();
    }
}
