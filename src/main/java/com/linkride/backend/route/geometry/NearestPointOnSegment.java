package com.linkride.backend.route.geometry;

/** Result of projecting a point onto a single line segment — see {@link GeoMath#nearestPointOnSegment}. */
public record NearestPointOnSegment(double lat, double lng, double fraction, double distanceMeters) {
}
