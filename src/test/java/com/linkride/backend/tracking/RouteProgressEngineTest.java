package com.linkride.backend.tracking;

import com.linkride.backend.location.GeoPoint;
import com.linkride.backend.ride.RideWaypoint;
import com.linkride.backend.route.geometry.BoundingBox;
import com.linkride.backend.route.geometry.GeoMath;
import com.linkride.backend.route.geometry.LatLng;
import com.linkride.backend.route.geometry.RouteGeometry;
import com.linkride.backend.route.geometry.RoutePoint;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pure unit tests against synthetic {@link RouteGeometry} fixtures, no Spring context — same
 * style as {@code RouteGeometryTest}/{@code CumulativeMetricsCalculatorTest} (§15).
 */
class RouteProgressEngineTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double LAT = 12.9716;
    private static final double START_LNG = 77.5990;
    private static final double END_LNG = 77.6090;

    private final TrackingProperties properties = new TrackingProperties();
    private final RouteProgressEngine engine = new RouteProgressEngine(properties);

    @Test
    void computeProgress_pointHalfwayAlongRoute_reportsHalfDistanceAndDuration() {
        RouteGeometry geometry = straightLineGeometry(1000);
        double midLng = (START_LNG + END_LNG) / 2;

        RouteProgress progress = engine.computeProgress(geometry, LAT, midLng, List.of());

        assertThat(progress.driverCumulativeDistanceMeters())
                .isCloseTo(geometry.totalDistanceMeters() / 2, within(1.0));
        assertThat(progress.remainingDistanceMeters())
                .isCloseTo(geometry.totalDistanceMeters() / 2, within(1.0));
        assertThat(progress.remainingDurationSeconds()).isCloseTo(500, within(1.0));
        assertThat(progress.onRoute()).isTrue();
    }

    @Test
    void computeProgress_atRouteEnd_reportsZeroRemaining() {
        RouteGeometry geometry = straightLineGeometry(1000);

        RouteProgress progress = engine.computeProgress(geometry, LAT, END_LNG, List.of());

        assertThat(progress.remainingDistanceMeters()).isCloseTo(0, within(1.0));
        assertThat(progress.remainingDurationSeconds()).isCloseTo(0, within(1.0));
    }

    @Test
    void computeProgress_pointFarFromRoute_isFlaggedOffRoute() {
        RouteGeometry geometry = straightLineGeometry(1000);

        // ~0.01 degrees due north of the route's start point -- well over the default 150m threshold.
        RouteProgress progress = engine.computeProgress(geometry, LAT + 0.01, START_LNG, List.of());

        assertThat(progress.distanceFromRouteMeters()).isGreaterThan(properties.getOffRouteThresholdMeters());
        assertThat(progress.onRoute()).isFalse();
    }

    @Test
    void computeProgress_pointOnTheRoute_isOnRoute() {
        RouteGeometry geometry = straightLineGeometry(1000);

        RouteProgress progress = engine.computeProgress(geometry, LAT, (START_LNG + END_LNG) / 2, List.of());

        assertThat(progress.onRoute()).isTrue();
    }

    @Test
    void computeProgress_waypointsOrderedBySequence_completedOnceDriverPassesThem() {
        RouteGeometry geometry = straightLineGeometry(1000);
        double quarterLng = START_LNG + (END_LNG - START_LNG) * 0.25;
        double threeQuarterLng = START_LNG + (END_LNG - START_LNG) * 0.75;
        RideWaypoint first = waypoint(0, LAT, quarterLng);
        RideWaypoint second = waypoint(1, LAT, threeQuarterLng);

        // Driver is at the midpoint: past the first waypoint, short of the second.
        double midLng = (START_LNG + END_LNG) / 2;
        RouteProgress progress = engine.computeProgress(geometry, LAT, midLng, List.of(second, first));

        assertThat(progress.waypoints()).hasSize(2);
        assertThat(progress.waypoints().get(0).sequence()).isEqualTo(0);
        assertThat(progress.waypoints().get(0).completed()).isTrue();
        assertThat(progress.waypoints().get(1).sequence()).isEqualTo(1);
        assertThat(progress.waypoints().get(1).completed()).isFalse();
    }

    @Test
    void computeProgress_zeroLengthRoute_doesNotDivideByZero() {
        RouteGeometry geometry = new RouteGeometry(
                List.of(new RoutePoint(LAT, START_LNG, 0, 0, 0)), 0, 0,
                BoundingBox.of(List.of(new LatLng(LAT, START_LNG))));

        RouteProgress progress = engine.computeProgress(geometry, LAT, START_LNG, List.of());

        assertThat(progress.remainingDurationSeconds()).isEqualTo(0);
    }

    private RouteGeometry straightLineGeometry(double totalDurationSeconds) {
        double totalDistance = GeoMath.haversineMeters(LAT, START_LNG, LAT, END_LNG);
        List<RoutePoint> points = List.of(
                new RoutePoint(LAT, START_LNG, 0, 0, 0),
                new RoutePoint(LAT, END_LNG, totalDistance, totalDurationSeconds, 1));
        List<LatLng> latLngs = points.stream().map(p -> new LatLng(p.lat(), p.lng())).toList();
        return new RouteGeometry(points, totalDistance, totalDurationSeconds, BoundingBox.of(latLngs));
    }

    private RideWaypoint waypoint(int sequence, double lat, double lng) {
        RideWaypoint waypoint = new RideWaypoint();
        waypoint.setWaypointId(UUID.randomUUID());
        waypoint.setSequence(sequence);
        waypoint.setLocation(new GeoPoint("Waypoint", "Address", GEOMETRY_FACTORY.createPoint(new Coordinate(lng, lat))));
        return waypoint;
    }
}
