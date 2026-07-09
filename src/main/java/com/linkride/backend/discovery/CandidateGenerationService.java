package com.linkride.backend.discovery;

import com.linkride.backend.ride.Ride;

import java.util.List;

/**
 * Produces the coarse candidate set of driver rides for a passenger search — seat capacity,
 * routing success, departure window, excluding the passenger's own rides, and (once available)
 * bounding-box overlap (design doc §2.4).
 *
 * <p><b>Scope boundary:</b> this stage performs only inexpensive metadata filtering. It must
 * never decode polylines, compute shared corridors, estimate pickup/drop points, or score rides
 * — those are Phase 2B.4 onward.</p>
 *
 * <p>Takes {@link PassengerSearchContext} rather than a growing parameter list, so future
 * criteria (preferences, blocked users, verified-only, organization scoping) can be read out of
 * the context without another signature change here.</p>
 */
public interface CandidateGenerationService {
    List<Ride> generateCandidates(PassengerSearchContext context);
}
