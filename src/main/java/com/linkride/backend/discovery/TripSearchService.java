package com.linkride.backend.discovery;

import java.util.UUID;

/**
 * Orchestrates passenger trip search. Kept as an interface — like {@link com.linkride.backend.route.RouteProvider}
 * elsewhere in the backend — so the orchestration contract stays stable while the pipeline behind
 * it (candidate generation, corridor computation, scoring, ranking) is built out across Phase
 * 2B.2–2B.9 without callers ever needing to change.
 */
public interface TripSearchService {
    TripSearchResponse search(UUID passengerId, TripSearchRequest request);
}
