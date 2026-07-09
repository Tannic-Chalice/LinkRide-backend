package com.linkride.backend.route.geometry;

import java.util.ArrayList;
import java.util.List;

/** Decodes a Google-encoded polyline (precision 1e5) into raw lat/lng points, in order. */
public final class PolylineCodec {

    private static final double PRECISION = 1e5;

    private PolylineCodec() {
    }

    public static List<LatLng> decode(String encodedPolyline) {
        List<LatLng> points = new ArrayList<>();
        if (encodedPolyline == null || encodedPolyline.isEmpty()) {
            return points;
        }

        int index = 0;
        int length = encodedPolyline.length();
        int lat = 0;
        int lng = 0;

        while (index < length) {
            int shift = 0;
            int result = 0;
            int b;
            do {
                b = encodedPolyline.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            lat += ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);

            shift = 0;
            result = 0;
            do {
                b = encodedPolyline.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            lng += ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);

            points.add(new LatLng(lat / PRECISION, lng / PRECISION));
        }

        return points;
    }
}
