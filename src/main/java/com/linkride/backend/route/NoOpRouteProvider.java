package com.linkride.backend.route;

import com.linkride.backend.location.GeoPointDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Zero-dependency {@link RouteProvider} for local development without a Google Maps API key.
 * Always reports {@link RouteGenerationState#PENDING} — no route was ever attempted.
 * Activate with {@code linkride.routing.provider=noop}; {@link com.linkride.backend.route.google.GoogleRouteProvider}
 * is the default.
 */
@Service
@ConditionalOnProperty(prefix = "linkride.routing", name = "provider", havingValue = "noop")
public class NoOpRouteProvider implements RouteProvider {

    @Override
    public RouteResult computeRoute(GeoPointDto pickup, List<GeoPointDto> waypoints, GeoPointDto destination) {
        return RouteResult.pending(null);
    }
}
