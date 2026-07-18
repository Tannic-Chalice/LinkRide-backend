package com.linkride.backend.route;

import com.linkride.backend.location.GeoPointDto;
import com.linkride.backend.route.geometry.GeoMath;
import com.linkride.backend.route.geometry.LatLng;
import com.linkride.backend.route.geometry.PolylineCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Free, deterministic-shape {@link RouteProvider} for the demo seeder (see {@code devtools}
 * package). Neither {@link com.linkride.backend.route.google.GoogleRouteProvider} (100+ live
 * Directions API calls per seed run — slow, costs money, rate-limited) nor {@link NoOpRouteProvider}
 * (always {@code PENDING}, so {@code Ride.routeGeometry} stays null and corridor
 * generation/scoring has nothing to work with) is usable for bulk seeding.
 *
 * <p>Fabricates a plausible multi-point road-like path — linear interpolation between
 * pickup/waypoints/destination with small perpendicular jitter, so the polyline isn't just a
 * dead-straight line — and derives distance/duration from it. Always reaches {@link
 * RouteGenerationState#READY}. Activate with {@code linkride.routing.provider=synthetic}; never
 * the default.</p>
 */
@Service
@ConditionalOnProperty(prefix = "linkride.routing", name = "provider", havingValue = "synthetic")
public class SyntheticRouteProvider implements RouteProvider {

    /** Road distance is never as direct as a straight line between two points. */
    private static final double ROAD_DIRECTNESS_FUDGE = 1.2;
    /** Average effective urban driving speed used to derive a duration from distance. */
    private static final double AVERAGE_SPEED_METERS_PER_SECOND = 8.3; // ~30 km/h
    private static final int POINTS_PER_LEG = 10;
    private static final double MAX_JITTER_DEGREES = 0.0015; // ~150m

    @Override
    public RouteResult computeRoute(GeoPointDto pickup, List<GeoPointDto> waypoints, GeoPointDto destination) {
        List<GeoPointDto> anchors = new ArrayList<>();
        anchors.add(pickup);
        if (waypoints != null) {
            anchors.addAll(waypoints);
        }
        anchors.add(destination);

        List<LatLng> path = buildJitteredPath(anchors);
        double distanceMeters = pathLengthMeters(path) * ROAD_DIRECTNESS_FUDGE;
        double durationSeconds = distanceMeters / AVERAGE_SPEED_METERS_PER_SECOND;

        String polyline = PolylineCodec.encode(path);
        return RouteResult.ready(polyline, (int) Math.round(distanceMeters), (int) Math.round(durationSeconds));
    }

    private List<LatLng> buildJitteredPath(List<GeoPointDto> anchors) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<LatLng> path = new ArrayList<>();
        path.add(new LatLng(anchors.get(0).getLatitude(), anchors.get(0).getLongitude()));

        for (int leg = 0; leg < anchors.size() - 1; leg++) {
            GeoPointDto from = anchors.get(leg);
            GeoPointDto to = anchors.get(leg + 1);

            for (int i = 1; i <= POINTS_PER_LEG; i++) {
                double t = (double) i / POINTS_PER_LEG;
                double lat = lerp(from.getLatitude(), to.getLatitude(), t);
                double lng = lerp(from.getLongitude(), to.getLongitude(), t);

                // Taper jitter to zero at each anchor so the path still passes through pickup,
                // every waypoint, and the destination exactly.
                double taper = Math.sin(Math.PI * t);
                double jitterLat = (random.nextDouble() * 2 - 1) * MAX_JITTER_DEGREES * taper;
                double jitterLng = (random.nextDouble() * 2 - 1) * MAX_JITTER_DEGREES * taper;

                path.add(new LatLng(lat + jitterLat, lng + jitterLng));
            }
        }

        return path;
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private double pathLengthMeters(List<LatLng> path) {
        double total = 0;
        for (int i = 1; i < path.size(); i++) {
            LatLng a = path.get(i - 1);
            LatLng b = path.get(i);
            total += GeoMath.haversineMeters(a.lat(), a.lng(), b.lat(), b.lng());
        }
        return total;
    }
}
