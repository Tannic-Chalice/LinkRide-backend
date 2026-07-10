package com.linkride.backend.discovery.scoring;

import com.linkride.backend.discovery.corridor.CorridorMatchCandidate;

/**
 * A validated corridor match (Phase 2B.6) paired with its scoring breakdown (Phase 2B.7, design
 * doc §6). Not yet sorted, tie-broken, or truncated to top-K — that's ranking (Phase 2B.8), which
 * consumes {@link MatchScore#compositeScore()} but owns list order.
 */
public record ScoredMatchCandidate(CorridorMatchCandidate match, MatchScore score) {
}
