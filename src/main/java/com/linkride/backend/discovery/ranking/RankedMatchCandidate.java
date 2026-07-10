package com.linkride.backend.discovery.ranking;

import com.linkride.backend.discovery.scoring.ScoredMatchCandidate;

/**
 * A scored corridor match (Phase 2B.7) annotated with its Pareto-optimality within this search's
 * result set (design doc §7.1): {@code paretoOptimal} is {@code true} unless some other surviving
 * candidate is at least as good on every {@link com.linkride.backend.discovery.scoring.MatchScore}
 * component and strictly better on at least one. List position (assigned by
 * {@link RankingService}, not stored here) is the actual best-first order — this flag is
 * supplementary labeling, e.g. for a client wanting to highlight "no strictly better option
 * exists" rather than relying on the composite score's exact ordering alone.
 */
public record RankedMatchCandidate(ScoredMatchCandidate scored, boolean paretoOptimal) {
}
