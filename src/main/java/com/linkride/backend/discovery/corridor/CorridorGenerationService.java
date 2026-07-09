package com.linkride.backend.discovery.corridor;

import com.linkride.backend.discovery.PassengerSearchContext;
import com.linkride.backend.ride.Ride;

import java.util.List;

/**
 * Computes shared-corridor candidates for the coarse candidate set produced by
 * {@code CandidateGenerationService} (Phase 2B.3). For each ride, runs the mandatory
 * bounding-box pre-check before any per-point geometry, then {@link CorridorCalculator}, then
 * discards runs below the configured minimum overlap fraction.
 *
 * <p>Kept as an interface, consistent with the rest of the discovery module's service seams.</p>
 */
public interface CorridorGenerationService {
    List<CorridorMatchCandidate> generateCorridors(PassengerSearchContext context, List<Ride> candidates);
}
