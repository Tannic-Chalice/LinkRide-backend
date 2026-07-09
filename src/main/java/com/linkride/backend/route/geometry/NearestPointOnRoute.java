package com.linkride.backend.route.geometry;

/**
 * Result of a point-to-polyline nearest-point query against a whole {@link RouteGeometry} — the
 * projected coordinate, how far into the route it falls (for ordering/pickup-drop derivation),
 * and how far away the original point was (the proximity/walk distance).
 */
public record NearestPointOnRoute(double lat, double lng, double cumulativeDistanceMeters, double distanceMeters) {
}
