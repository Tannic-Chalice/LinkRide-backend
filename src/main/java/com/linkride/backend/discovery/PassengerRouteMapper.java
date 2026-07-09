package com.linkride.backend.discovery;

import com.linkride.backend.route.geometry.RouteGeometry;
import com.linkride.backend.route.geometry.RouteGeometryBuilder;
import com.linkride.backend.route.RouteGenerationState;
import com.linkride.backend.route.RouteResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Maps a routing-provider result into the discovery module's own {@link PassengerRoute} shape.
 * Kept separate from {@link PassengerRoute} — a plain data holder — because this mapping only
 * grows more involved as later milestones add more derived data; a dedicated mapper gives that
 * growth a home without turning the domain object into logic.
 *
 * <p>A concrete component rather than an interface, matching {@link com.linkride.backend.route.google.GoogleRouteMapper}'s
 * convention elsewhere in the backend — there's exactly one way to build a {@code PassengerRoute}
 * from a {@code RouteResult}, not a family of swappable strategies.</p>
 */
@Component
@RequiredArgsConstructor
public class PassengerRouteMapper {

    private final RouteGeometryBuilder routeGeometryBuilder;

    public PassengerRoute toPassengerRoute(RouteResult routeResult) {
        RouteGeometry geometry = routeResult.getState() == RouteGenerationState.READY
                ? routeGeometryBuilder.build(routeResult.getPolyline(), routeResult.getDistanceMeters(), routeResult.getDurationSeconds())
                : null;

        return PassengerRoute.builder()
                .polyline(routeResult.getPolyline())
                .distanceMeters(routeResult.getDistanceMeters())
                .durationSeconds(routeResult.getDurationSeconds())
                .routeGenerationState(routeResult.getState())
                .geometry(geometry)
                .build();
    }
}
