package com.linkride.backend.route;

import com.linkride.backend.location.GeoPointDto;

import java.util.List;

/**
 * Abstraction over an external directions/routing service. Keeps {@code RideService}
 * decoupled from any specific maps SDK (Google Directions, Mapbox, ...) — swapping
 * providers later means adding a new implementation here, nothing upstream changes.
 */
public interface RouteProvider {
    RouteResult computeRoute(GeoPointDto pickup, List<GeoPointDto> waypoints, GeoPointDto destination);
}
