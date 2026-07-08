package com.linkride.backend.location;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.Point;

/**
 * Converts between {@link GeoPointDto} (lat/lng) and {@link GeoPoint} (JTS {@link Point}).
 *
 * <p>JTS coordinates are (x, y) = (longitude, latitude) — the reverse of the usual
 * "lat, lng" reading order. Getting this backwards silently swaps every coordinate
 * on the globe, so all conversion happens here, once.</p>
 */
public final class GeoPoints {

    private static final int SRID_WGS84 = 4326;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), SRID_WGS84);

    private GeoPoints() {
    }

    public static GeoPoint fromDto(GeoPointDto dto) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(dto.getLongitude(), dto.getLatitude()));
        return new GeoPoint(dto.getName(), dto.getAddress(), point);
    }

    public static GeoPointDto toDto(GeoPoint geoPoint) {
        GeoPointDto dto = new GeoPointDto();
        dto.setName(geoPoint.getName());
        dto.setAddress(geoPoint.getAddress());
        dto.setLatitude(geoPoint.getPoint().getY());
        dto.setLongitude(geoPoint.getPoint().getX());
        return dto;
    }
}
