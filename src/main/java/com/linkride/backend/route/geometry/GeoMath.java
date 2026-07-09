package com.linkride.backend.route.geometry;

/**
 * Pure geospatial math — no dependency on any route/entity type. Everything here operates on
 * plain lat/lng doubles so it can be unit tested with hand-computed coordinates.
 */
public final class GeoMath {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    private GeoMath() {
    }

    /** Great-circle distance between two coordinates, in meters. */
    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Nearest point on line segment start→end to the given point — a point-to-*segment* query,
     * not point-to-vertex (design doc §5.1: "the closest approach may fall between two of the
     * driver's simplified vertices"). Uses a local equirectangular projection: accurate enough at
     * the segment lengths a decoded route has (tens to low hundreds of meters) and far cheaper
     * than exact great-circle segment math. The fractional position is clamped to [0, 1], so the
     * result always lies on the segment itself, never on its extension.
     */
    public static NearestPointOnSegment nearestPointOnSegment(
            double lat, double lng,
            double startLat, double startLng,
            double endLat, double endLng) {

        double refLatRad = Math.toRadians((startLat + endLat) / 2.0);
        double metersPerDegreeLng = METERS_PER_DEGREE_LAT * Math.cos(refLatRad);

        double ax = startLng * metersPerDegreeLng;
        double ay = startLat * METERS_PER_DEGREE_LAT;
        double bx = endLng * metersPerDegreeLng;
        double by = endLat * METERS_PER_DEGREE_LAT;
        double px = lng * metersPerDegreeLng;
        double py = lat * METERS_PER_DEGREE_LAT;

        double abx = bx - ax;
        double aby = by - ay;
        double lengthSquared = abx * abx + aby * aby;

        double t = lengthSquared == 0.0
                ? 0.0
                : ((px - ax) * abx + (py - ay) * aby) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));

        double nearestLat = startLat + t * (endLat - startLat);
        double nearestLng = startLng + t * (endLng - startLng);
        double distanceMeters = haversineMeters(lat, lng, nearestLat, nearestLng);

        return new NearestPointOnSegment(nearestLat, nearestLng, t, distanceMeters);
    }
}
