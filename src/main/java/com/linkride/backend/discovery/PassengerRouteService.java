package com.linkride.backend.discovery;

/**
 * Computes the passenger's own origin→destination route for a search, via the same
 * {@link com.linkride.backend.route.RouteProvider} already used for driver ride creation
 * (Phase 2A). This is the passenger's actual chosen path — what shared-corridor computation
 * (Phase 2B.5) reasons over, not just their two endpoints (design doc §1, §2.2, §3).
 *
 * <p>Kept as an interface, consistent with {@link TripSearchService} and
 * {@link CandidateGenerationService} elsewhere in this package.</p>
 */
public interface PassengerRouteService {
    PassengerRoute computeRoute(TripSearchRequest request);
}
