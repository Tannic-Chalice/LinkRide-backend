package com.linkride.backend.route.geometry;

import java.util.List;

/**
 * Axis-aligned lat/lng envelope of a route — the cheap AABB pre-filter from design doc §2.4,
 * meant to eliminate obviously unrelated routes before any per-point geometry runs. Not wired
 * into candidate generation yet (Phase 2B.3 documents that gap); this exists so that filter can
 * be added without introducing new geometry math at the same time.
 */
public record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {

    public static BoundingBox of(List<LatLng> points) {
        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLng = Double.MAX_VALUE;
        double maxLng = -Double.MAX_VALUE;

        for (LatLng point : points) {
            minLat = Math.min(minLat, point.lat());
            maxLat = Math.max(maxLat, point.lat());
            minLng = Math.min(minLng, point.lng());
            maxLng = Math.max(maxLng, point.lng());
        }

        return new BoundingBox(minLat, maxLat, minLng, maxLng);
    }

    /** True if this box and {@code other}, each padded by {@code paddingMeters}, overlap. */
    public boolean overlaps(BoundingBox other, double paddingMeters) {
        double latPaddingDegrees = paddingMeters / 111_320.0;
        double avgLatRad = Math.toRadians((minLat + maxLat) / 2.0);
        double lngPaddingDegrees = paddingMeters / (111_320.0 * Math.cos(avgLatRad));

        boolean latOverlap = (minLat - latPaddingDegrees) <= (other.maxLat() + latPaddingDegrees)
                && (maxLat + latPaddingDegrees) >= (other.minLat() - latPaddingDegrees);
        boolean lngOverlap = (minLng - lngPaddingDegrees) <= (other.maxLng() + lngPaddingDegrees)
                && (maxLng + lngPaddingDegrees) >= (other.minLng() - lngPaddingDegrees);

        return latOverlap && lngOverlap;
    }
}
