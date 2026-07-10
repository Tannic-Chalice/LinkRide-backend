package com.linkride.backend.discovery.ranking;

import com.linkride.backend.discovery.PassengerSearchContext;
import com.linkride.backend.discovery.scoring.ScoredMatchCandidate;

import java.util.List;

/**
 * Phase 2B.8: sorts scored candidates (Phase 2B.7) best-first, tags each with whether it's
 * Pareto-optimal within this result set (design doc §7.1), and truncates to
 * {@link com.linkride.backend.discovery.MatchingProperties#getMaxRankedMatches()}. Unlike every
 * earlier pipeline stage, output order here is the actual contract, not incidental.
 */
public interface RankingService {

    List<RankedMatchCandidate> rank(PassengerSearchContext context, List<ScoredMatchCandidate> scoredMatches);
}
