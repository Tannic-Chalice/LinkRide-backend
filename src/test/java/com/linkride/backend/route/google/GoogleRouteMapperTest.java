package com.linkride.backend.route.google;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkride.backend.route.RouteFailureReason;
import com.linkride.backend.route.RouteGenerationState;
import com.linkride.backend.route.RouteResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleRouteMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GoogleRouteMapper mapper = new GoogleRouteMapper();

    private RouteResult mapJson(String json) throws Exception {
        return mapper.map(objectMapper.readValue(json, GoogleDirectionsResponse.class));
    }

    @Test
    void mapsOkResponseWithSingleLegToReady() throws Exception {
        String json = """
                {
                  "status": "OK",
                  "routes": [
                    {
                      "overview_polyline": { "points": "abc123" },
                      "legs": [
                        { "distance": { "value": 5000 }, "duration": { "value": 600 } }
                      ]
                    }
                  ]
                }
                """;

        RouteResult result = mapJson(json);

        assertThat(result.getState()).isEqualTo(RouteGenerationState.READY);
        assertThat(result.getPolyline()).isEqualTo("abc123");
        assertThat(result.getDistanceMeters()).isEqualTo(5000);
        assertThat(result.getDurationSeconds()).isEqualTo(600);
        assertThat(result.getFailureReason()).isNull();
    }

    @Test
    void sumsDistanceAndDurationAcrossMultipleLegsForWaypoints() throws Exception {
        String json = """
                {
                  "status": "OK",
                  "routes": [
                    {
                      "overview_polyline": { "points": "xyz789" },
                      "legs": [
                        { "distance": { "value": 3000 }, "duration": { "value": 300 } },
                        { "distance": { "value": 2000 }, "duration": { "value": 200 } }
                      ]
                    }
                  ]
                }
                """;

        RouteResult result = mapJson(json);

        assertThat(result.getState()).isEqualTo(RouteGenerationState.READY);
        assertThat(result.getDistanceMeters()).isEqualTo(5000);
        assertThat(result.getDurationSeconds()).isEqualTo(500);
    }

    @Test
    void mapsZeroResultsToUnroutable() throws Exception {
        RouteResult result = mapJson("{ \"status\": \"ZERO_RESULTS\", \"routes\": [] }");

        assertThat(result.getState()).isEqualTo(RouteGenerationState.UNROUTABLE);
        assertThat(result.getFailureReason()).isEqualTo(RouteFailureReason.ZERO_RESULTS);
    }

    @Test
    void mapsInvalidRequestToUnroutable() throws Exception {
        RouteResult result = mapJson("{ \"status\": \"INVALID_REQUEST\" }");

        assertThat(result.getState()).isEqualTo(RouteGenerationState.UNROUTABLE);
        assertThat(result.getFailureReason()).isEqualTo(RouteFailureReason.INVALID_REQUEST);
    }

    @Test
    void mapsNotFoundToUnroutable() throws Exception {
        RouteResult result = mapJson("{ \"status\": \"NOT_FOUND\" }");

        assertThat(result.getState()).isEqualTo(RouteGenerationState.UNROUTABLE);
        assertThat(result.getFailureReason()).isEqualTo(RouteFailureReason.NOT_FOUND);
    }

    @Test
    void mapsRequestDeniedToUnroutable() throws Exception {
        RouteResult result = mapJson("{ \"status\": \"REQUEST_DENIED\", \"error_message\": \"bad key\" }");

        assertThat(result.getState()).isEqualTo(RouteGenerationState.UNROUTABLE);
        assertThat(result.getFailureReason()).isEqualTo(RouteFailureReason.REQUEST_DENIED);
    }

    @Test
    void mapsMaxWaypointsExceededToUnroutable() throws Exception {
        RouteResult result = mapJson("{ \"status\": \"MAX_WAYPOINTS_EXCEEDED\" }");

        assertThat(result.getState()).isEqualTo(RouteGenerationState.UNROUTABLE);
        assertThat(result.getFailureReason()).isEqualTo(RouteFailureReason.MAX_WAYPOINTS_EXCEEDED);
    }

    @Test
    void mapsOverQueryLimitToPendingForRetryLater() throws Exception {
        RouteResult result = mapJson("{ \"status\": \"OVER_QUERY_LIMIT\" }");

        assertThat(result.getState()).isEqualTo(RouteGenerationState.PENDING);
        assertThat(result.getFailureReason()).isEqualTo(RouteFailureReason.OVER_QUERY_LIMIT);
    }

    @Test
    void mapsUnknownErrorToPending() throws Exception {
        RouteResult result = mapJson("{ \"status\": \"UNKNOWN_ERROR\" }");

        assertThat(result.getState()).isEqualTo(RouteGenerationState.PENDING);
        assertThat(result.getFailureReason()).isEqualTo(RouteFailureReason.UNKNOWN_ERROR);
    }

    @Test
    void mapsOkWithEmptyRoutesToPendingUnknownError() throws Exception {
        RouteResult result = mapJson("{ \"status\": \"OK\", \"routes\": [] }");

        assertThat(result.getState()).isEqualTo(RouteGenerationState.PENDING);
        assertThat(result.getFailureReason()).isEqualTo(RouteFailureReason.UNKNOWN_ERROR);
    }

    @Test
    void mapsUnrecognizedStatusToPendingUnknownError() throws Exception {
        RouteResult result = mapJson("{ \"status\": \"SOME_FUTURE_STATUS\" }");

        assertThat(result.getState()).isEqualTo(RouteGenerationState.PENDING);
        assertThat(result.getFailureReason()).isEqualTo(RouteFailureReason.UNKNOWN_ERROR);
    }
}
