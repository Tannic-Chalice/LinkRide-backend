package com.linkride.backend.discovery.corridor;

import com.linkride.backend.discovery.MatchingProperties;
import com.linkride.backend.discovery.PassengerRoute;
import com.linkride.backend.discovery.PassengerSearchContext;
import com.linkride.backend.discovery.TripSearchRequest;
import com.linkride.backend.ride.Ride;
import com.linkride.backend.route.geometry.NearestPointOnRoute;
import com.linkride.backend.route.geometry.RouteGeometry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 2B.5: for each coarse candidate, reject on bounding-box overlap first (mandatory,
 * cheap — no per-point geometry touched otherwise), then run {@link CorridorCalculator}, then
 * discard runs below {@link MatchingProperties#getMinOverlapFraction()}. Surviving runs get
 * pickup/drop derived by projecting their entry/exit points onto the driver's route.
 *
 * <p>Deliberately no additional geometry abstractions or spatial indexing here (per design doc
 * §2's own V1 guidance) — the bounding-box check is the only pre-filter, and it operates on the
 * {@link RouteGeometry#boundingBox()} that's already precomputed once at decode time (Phase
 * 2B.4), not recomputed here.</p>
 */
@Service
@RequiredArgsConstructor
public class CorridorGenerationServiceImpl implements CorridorGenerationService {

    private final MatchingProperties matchingProperties;

    @Override
    public List<CorridorMatchCandidate> generateCorridors(PassengerSearchContext context, List<Ride> candidates) {
        PassengerRoute passengerRoute = context.getPassengerRoute();
        RouteGeometry passengerGeometry = passengerRoute.getGeometry();
        if (passengerGeometry == null) {
            return List.of();
        }

        double walkToleranceMeters = resolveWalkToleranceMeters(context.getRequest());

        List<CorridorMatchCandidate> results = new ArrayList<>();
        for (Ride ride : candidates) {
            RouteGeometry driverGeometry = ride.getRouteGeometry();
            if (driverGeometry == null) {
                continue;
            }

            // Mandatory first-stage rejection — bounding boxes padded by the same tolerance used
            // for per-point coverage is a valid superset test: if the padded boxes don't overlap,
            // no point on either route can be within tolerance of the other, so nothing further
            // needs to run for this ride at all.
            if (!passengerGeometry.boundingBox().overlaps(driverGeometry.boundingBox(), walkToleranceMeters)) {
                continue;
            }

            List<CorridorRun> runs = CorridorCalculator.computeCorridorRuns(passengerGeometry, driverGeometry, walkToleranceMeters);

            for (CorridorRun run : runs) {
                if (run.overlapFraction() < matchingProperties.getMinOverlapFraction()) {
                    continue;
                }

                NearestPointOnRoute pickup = driverGeometry.nearestPoint(run.entryPoint().lat(), run.entryPoint().lng());
                NearestPointOnRoute drop = driverGeometry.nearestPoint(run.exitPoint().lat(), run.exitPoint().lng());
                results.add(new CorridorMatchCandidate(ride, run, pickup, drop));
            }
        }

        return results;
    }

    private double resolveWalkToleranceMeters(TripSearchRequest request) {
        Integer toPickup = request.getMaxWalkToPickupMeters();
        Integer fromDrop = request.getMaxWalkFromDropMeters();

        if (toPickup == null && fromDrop == null) {
            return matchingProperties.getCorridorWalkToleranceMeters();
        }

        return Math.max(toPickup != null ? toPickup : 0, fromDrop != null ? fromDrop : 0);
    }
}
