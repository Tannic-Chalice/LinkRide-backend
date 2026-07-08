package com.linkride.backend.route.google;

import com.linkride.backend.route.RouteFailureReason;
import com.linkride.backend.route.RouteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Translates a {@link GoogleDirectionsResponse} into a provider-agnostic {@link RouteResult}.
 * The only class in the codebase that knows Google's status-string vocabulary — if Google
 * changes its response schema, this is the sole class that needs to change.
 */
@Slf4j
@Component
public class GoogleRouteMapper {

    public RouteResult map(GoogleDirectionsResponse response) {
        String status = response.getStatus();
        if (status == null) {
            log.warn("Google Directions response had no status field");
            return RouteResult.pending(RouteFailureReason.UNKNOWN_ERROR);
        }

        return switch (status) {
            case "OK" -> mapOkResponse(response);
            case "OVER_QUERY_LIMIT" -> RouteResult.pending(RouteFailureReason.OVER_QUERY_LIMIT);
            case "UNKNOWN_ERROR" -> RouteResult.pending(RouteFailureReason.UNKNOWN_ERROR);
            case "ZERO_RESULTS" -> RouteResult.unroutable(RouteFailureReason.ZERO_RESULTS);
            case "NOT_FOUND" -> RouteResult.unroutable(RouteFailureReason.NOT_FOUND);
            case "INVALID_REQUEST" -> RouteResult.unroutable(RouteFailureReason.INVALID_REQUEST);
            case "MAX_WAYPOINTS_EXCEEDED" -> RouteResult.unroutable(RouteFailureReason.MAX_WAYPOINTS_EXCEEDED);
            case "REQUEST_DENIED" -> RouteResult.unroutable(RouteFailureReason.REQUEST_DENIED);
            default -> {
                log.warn("Unrecognized Google Directions status: {}", status);
                yield RouteResult.pending(RouteFailureReason.UNKNOWN_ERROR);
            }
        };
    }

    private RouteResult mapOkResponse(GoogleDirectionsResponse response) {
        List<GoogleDirectionsResponse.Route> routes = response.getRoutes();
        if (routes == null || routes.isEmpty()) {
            log.warn("Google Directions returned status=OK with no routes");
            return RouteResult.pending(RouteFailureReason.UNKNOWN_ERROR);
        }

        GoogleDirectionsResponse.Route route = routes.get(0);
        String polyline = route.getOverviewPolyline() != null ? route.getOverviewPolyline().getPoints() : null;
        List<GoogleDirectionsResponse.Leg> legs = route.getLegs();
        if (polyline == null || legs == null || legs.isEmpty()) {
            log.warn("Google Directions status=OK response missing polyline or legs");
            return RouteResult.pending(RouteFailureReason.UNKNOWN_ERROR);
        }

        int totalDistanceMeters = 0;
        int totalDurationSeconds = 0;
        for (GoogleDirectionsResponse.Leg leg : legs) {
            if (leg.getDistance() == null || leg.getDuration() == null
                    || leg.getDistance().getValue() == null || leg.getDuration().getValue() == null) {
                log.warn("Google Directions leg missing distance/duration");
                return RouteResult.pending(RouteFailureReason.UNKNOWN_ERROR);
            }
            totalDistanceMeters += leg.getDistance().getValue();
            totalDurationSeconds += leg.getDuration().getValue();
        }

        return RouteResult.ready(polyline, totalDistanceMeters, totalDurationSeconds);
    }
}
