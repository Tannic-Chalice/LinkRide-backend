package com.linkride.backend.location;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

/**
 * Reusable named coordinate: a place with a human-readable name/address plus a
 * PostGIS geography point. Embedded (with column-name overrides) wherever a
 * ride needs a location — pickup, destination, or a waypoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class GeoPoint {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address", columnDefinition = "TEXT", nullable = false)
    private String address;

    @Column(name = "point", columnDefinition = "geography(Point,4326)", nullable = false)
    private Point point;
}
