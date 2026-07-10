package com.linkride.backend.discovery.scoring;

import com.linkride.backend.discovery.PassengerSearchContext;
import com.linkride.backend.discovery.corridor.CorridorMatchCandidate;

import java.util.List;

/**
 * Phase 2B.7: computes the multi-criteria cost/benefit breakdown (design doc §6) for every
 * hard-constraint-validated corridor candidate (Phase 2B.6), including the passenger-detour-ratio
 * hard-reject from §6.2a. Candidates surviving that reject each get a normalized, weighted
 * composite score (§6.5). Output order is not meaningful — no sorting, tie-breaking, or top-K
 * truncation happens here (Phase 2B.8).
 */
public interface ScoringService {

    List<ScoredMatchCandidate> score(PassengerSearchContext context, List<CorridorMatchCandidate> candidates);
}
