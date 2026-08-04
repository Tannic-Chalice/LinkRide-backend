package com.linkride.backend.tracking;

import com.linkride.backend.ride.RideWaypoint;
import com.linkride.backend.route.geometry.NearestPointOnRoute;
import com.linkride.backend.route.geometry.RouteGeometry;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * GPS fix -&gt; route progress (§8). Reuses {@link RouteGeometry#nearestPoint} — already the
 * exact point-to-polyline projection this needs. Waypoint projections are recomputed fresh on
 * every call rather than memoized (§6.1): the dominant per-ping cost is the {@code Ride}
 * fetch/JSON deserialization this class doesn't pay for, not these few in-memory
 * {@code nearestPoint} scans against an already-loaded geometry.
 */
@Component
@RequiredArgsConstructor
public class RouteProgressEngine {

    /**
     * A waypoint is marked completed once the driver's cumulative distance is within this many
     * meters of it — absorbs GPS jitter near the waypoint without needing to remember any prior
     * state, since completion is recomputed from scratch on every call (§8 step 4).
     */
    private static final double WAYPOINT_COMPLETION_TOLERANCE_METERS = 25.0;

    private final TrackingProperties properties;

    public RouteProgress computeProgress(RouteGeometry geometry, double lat, double lng, List<RideWaypoint> waypoints) {
        NearestPointOnRoute nearest = geometry.nearestPoint(lat, lng);
        double totalDistance = geometry.totalDistanceMeters();
        double driverCumulativeDistanceMeters = nearest.cumulativeDistanceMeters();
        double remainingDistanceMeters = Math.max(0, totalDistance - driverCumulativeDistanceMeters);
        double remainingDurationSeconds = totalDistance == 0
                ? 0
                : geometry.totalDurationSeconds() * (remainingDistanceMeters / totalDistance);
        boolean onRoute = nearest.distanceMeters() <= properties.getOffRouteThresholdMeters();

        List<WaypointProgress> waypointProgress = waypoints.stream()
                .sorted(Comparator.comparing(RideWaypoint::getSequence))
                .map(waypoint -> toWaypointProgress(geometry, waypoint, driverCumulativeDistanceMeters))
                .toList();

        return new RouteProgress(
                driverCumulativeDistanceMeters,
                remainingDistanceMeters,
                remainingDurationSeconds,
                nearest.distanceMeters(),
                onRoute,
                waypointProgress);
    }

    private WaypointProgress toWaypointProgress(RouteGeometry geometry, RideWaypoint waypoint, double driverCumulativeDistanceMeters) {
        Point point = waypoint.getLocation().getPoint();
        NearestPointOnRoute projection = geometry.nearestPoint(point.getY(), point.getX());
        boolean completed = driverCumulativeDistanceMeters + WAYPOINT_COMPLETION_TOLERANCE_METERS
                >= projection.cumulativeDistanceMeters();
        return new WaypointProgress(
                waypoint.getWaypointId(), waypoint.getSequence(), projection.cumulativeDistanceMeters(), completed);
    }
}
