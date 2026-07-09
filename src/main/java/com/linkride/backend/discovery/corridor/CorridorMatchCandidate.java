package com.linkride.backend.discovery.corridor;

import com.linkride.backend.ride.Ride;
import com.linkride.backend.route.geometry.NearestPointOnRoute;

/**
 * A ride that survived shared-corridor computation for one covered run, with pickup/drop derived
 * as that run's entry/exit points projected onto the driver's own route (design doc §5.2).
 * {@code pickup}/{@code drop} are {@link NearestPointOnRoute} — not a new shape — because that's
 * exactly what projecting a passenger-route point onto the driver's route already produces:
 * a coordinate, how far into the driver's route it falls (needed for detour cost, Phase 2B.7),
 * and how far the passenger would need to walk to reach it.
 *
 * <p>No hard-constraint validation (seat capacity re-check, time-window, drop-before-destination)
 * has run yet — that's Phase 2B.6. No score exists yet — that's Phase 2B.7. This is purely "a
 * ride shares a meaningful enough leg of the passenger's journey."</p>
 */
public record CorridorMatchCandidate(Ride ride, CorridorRun corridorRun, NearestPointOnRoute pickup, NearestPointOnRoute drop) {
}
