package com.linkride.backend.discovery.corridor;

import com.linkride.backend.route.geometry.NearestPointOnRoute;
import com.linkride.backend.route.geometry.RouteGeometry;
import com.linkride.backend.route.geometry.RoutePoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes the shared corridor between a passenger's route and a driver's route (design doc
 * §5.1) — the maximal contiguous run(s) of the passenger's own route within walking distance of
 * the driver's route. Returns every run found, unfiltered; deciding which runs are "good enough"
 * (the {@code minOverlapFraction} gate) is policy, not algorithm, and lives in
 * {@link CorridorGenerationServiceImpl}.
 *
 * <p>A plain static utility, not a Spring bean — like {@link com.linkride.backend.route.geometry.CumulativeMetricsCalculator},
 * this is pure geometry with no external dependency.</p>
 */
public final class CorridorCalculator {

    private CorridorCalculator() {
    }

    public static List<CorridorRun> computeCorridorRuns(
            RouteGeometry passengerGeometry, RouteGeometry driverGeometry, double walkToleranceMeters) {

        List<RoutePoint> passengerPoints = passengerGeometry.points();
        boolean[] covered = new boolean[passengerPoints.size()];

        for (int i = 0; i < passengerPoints.size(); i++) {
            RoutePoint point = passengerPoints.get(i);
            NearestPointOnRoute nearest = driverGeometry.nearestPoint(point.lat(), point.lng());
            covered[i] = nearest.distanceMeters() <= walkToleranceMeters;
        }

        List<CorridorRun> runs = new ArrayList<>();
        int runStart = -1;
        for (int i = 0; i < covered.length; i++) {
            if (covered[i] && runStart == -1) {
                runStart = i;
            } else if (!covered[i] && runStart != -1) {
                runs.add(buildRun(passengerGeometry, runStart, i - 1));
                runStart = -1;
            }
        }
        if (runStart != -1) {
            runs.add(buildRun(passengerGeometry, runStart, covered.length - 1));
        }

        return runs;
    }

    private static CorridorRun buildRun(RouteGeometry passengerGeometry, int entryIndex, int exitIndex) {
        RoutePoint entry = passengerGeometry.points().get(entryIndex);
        RoutePoint exit = passengerGeometry.points().get(exitIndex);

        double runLength = exit.cumulativeDistanceMeters() - entry.cumulativeDistanceMeters();
        double overlapFraction = passengerGeometry.totalDistanceMeters() == 0
                ? 0
                : runLength / passengerGeometry.totalDistanceMeters();

        return new CorridorRun(entry, exit, overlapFraction);
    }
}
