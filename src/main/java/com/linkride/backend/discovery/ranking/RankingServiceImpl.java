package com.linkride.backend.discovery.ranking;

import com.linkride.backend.discovery.MatchingProperties;
import com.linkride.backend.discovery.PassengerSearchContext;
import com.linkride.backend.discovery.scoring.MatchScore;
import com.linkride.backend.discovery.scoring.ScoredMatchCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Phase 2B.8 implementation of design doc §7. Sorts ascending by composite score (lower = better,
 * §6.5), tie-breaking first by {@code driverDetourTimeSeconds} (§7: "prefer smaller absolute
 * detour time") and then by ride ID for full determinism — the doc's own next tie-break, "higher
 * driver rating if/when a reputation system exists," has no reputation system to consult yet, so
 * there's nothing to insert between those two.
 *
 * <p>Every candidate is also tagged with Pareto-optimality (§7.1) computed over the *post-sort,
 * pre-truncation* set: with all-non-negative composite-score weights (§6.5), a dominated candidate
 * can never out-rank its dominator by construction, so the frontier computation doesn't change
 * sort order — it only supplies the {@code paretoOptimal} label itself.</p>
 */
@Service
@RequiredArgsConstructor
public class RankingServiceImpl implements RankingService {

    private final MatchingProperties matchingProperties;

    @Override
    public List<RankedMatchCandidate> rank(PassengerSearchContext context, List<ScoredMatchCandidate> scoredMatches) {
        if (scoredMatches.isEmpty()) {
            return List.of();
        }

        List<ScoredMatchCandidate> sorted = scoredMatches.stream()
                .sorted(Comparator
                        .comparingDouble((ScoredMatchCandidate c) -> c.score().compositeScore())
                        .thenComparingDouble(c -> c.score().driverDetourTimeSeconds())
                        .thenComparing(c -> c.match().ride().getRideId()))
                .collect(Collectors.toList());

        List<RankedMatchCandidate> ranked = new ArrayList<>(sorted.size());
        for (ScoredMatchCandidate candidate : sorted) {
            ranked.add(new RankedMatchCandidate(candidate, isParetoOptimal(candidate, sorted)));
        }

        int limit = matchingProperties.getMaxRankedMatches();
        return ranked.size() > limit ? ranked.subList(0, limit) : ranked;
    }

    private boolean isParetoOptimal(ScoredMatchCandidate candidate, List<ScoredMatchCandidate> all) {
        for (ScoredMatchCandidate other : all) {
            if (other == candidate) {
                continue;
            }
            if (dominates(other.score(), candidate.score())) {
                return false;
            }
        }
        return true;
    }

    /**
     * True if {@code challenger} is at least as good as {@code candidate} on every §6 cost
     * component and strictly better on at least one (design doc §7.1).
     */
    private boolean dominates(MatchScore challenger, MatchScore candidate) {
        boolean atLeastAsGoodOnEvery =
                challenger.driverDetourDistanceMeters() <= candidate.driverDetourDistanceMeters()
                        && challenger.driverDetourTimeSeconds() <= candidate.driverDetourTimeSeconds()
                        && challenger.passengerDetourRatio() <= candidate.passengerDetourRatio()
                        && challenger.walkFraction() <= candidate.walkFraction()
                        && (1 - challenger.benefitRatio()) <= (1 - candidate.benefitRatio())
                        && challenger.timeWindowOffsetMinutes() <= candidate.timeWindowOffsetMinutes();

        boolean strictlyBetterOnSome =
                challenger.driverDetourDistanceMeters() < candidate.driverDetourDistanceMeters()
                        || challenger.driverDetourTimeSeconds() < candidate.driverDetourTimeSeconds()
                        || challenger.passengerDetourRatio() < candidate.passengerDetourRatio()
                        || challenger.walkFraction() < candidate.walkFraction()
                        || (1 - challenger.benefitRatio()) < (1 - candidate.benefitRatio())
                        || challenger.timeWindowOffsetMinutes() < candidate.timeWindowOffsetMinutes();

        return atLeastAsGoodOnEvery && strictlyBetterOnSome;
    }
}
