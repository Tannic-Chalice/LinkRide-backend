package com.linkride.backend.route.google;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Dumb 1:1 mapping of the Google Directions API JSON response. No business logic —
 * {@link GoogleRouteMapper} is the only class that interprets these fields.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleDirectionsResponse {

    private String status;

    @JsonProperty("error_message")
    private String errorMessage;

    private List<Route> routes;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Route {

        @JsonProperty("overview_polyline")
        private OverviewPolyline overviewPolyline;

        private List<Leg> legs;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OverviewPolyline {
        private String points;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Leg {
        private ValueField distance;
        private ValueField duration;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ValueField {
        private Integer value;
        private String text;
    }
}
