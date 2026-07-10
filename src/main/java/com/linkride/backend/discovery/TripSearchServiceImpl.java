package com.linkride.backend.discovery;

import com.linkride.backend.discovery.corridor.CorridorGenerationService;
import com.linkride.backend.discovery.corridor.CorridorMatchCandidate;
import com.linkride.backend.discovery.ranking.RankedMatchCandidate;
import com.linkride.backend.discovery.ranking.RankingService;
import com.linkride.backend.discovery.scoring.ScoredMatchCandidate;
import com.linkride.backend.discovery.scoring.ScoringService;
import com.linkride.backend.discovery.validation.MatchValidationService;
import com.linkride.backend.ride.Ride;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Phase 2B.8 scope — the full pipeline: validate the request, compute the passenger's own route,
 * assemble the {@link PassengerSearchContext}, run coarse candidate generation, run
 * shared-corridor computation, run hard-constraint validation, score every surviving candidate,
 * rank and truncate to top-K, and map the result to a match list. This is the last stage in the
 * design doc's algorithm (§4) — every stage now has an implementation, though weights, tolerances,
 * and thresholds throughout remain hand-tuned defaults per each stage's own documented rationale,
 * not yet learned from outcome data (see design doc §6.5's own note on that eventual evolution).
 */
@Service
@RequiredArgsConstructor
public class TripSearchServiceImpl implements TripSearchService {

    private final TripSearchRequestValidator validator;
    private final PassengerRouteService passengerRouteService;
    private final CandidateGenerationService candidateGenerationService;
    private final CorridorGenerationService corridorGenerationService;
    private final MatchValidationService matchValidationService;
    private final ScoringService scoringService;
    private final RankingService rankingService;

    @Override
    public TripSearchResponse search(UUID passengerId, TripSearchRequest request) {
        validator.validate(request);

        PassengerRoute passengerRoute = passengerRouteService.computeRoute(request);

        PassengerSearchContext context = PassengerSearchContext.builder()
                .passengerId(passengerId)
                .request(request)
                .passengerRoute(passengerRoute)
                .build();

        List<Ride> candidates = candidateGenerationService.generateCandidates(context);
        List<CorridorMatchCandidate> corridorMatches = corridorGenerationService.generateCorridors(context, candidates);
        List<CorridorMatchCandidate> validatedMatches = matchValidationService.validate(context, corridorMatches);
        List<ScoredMatchCandidate> scoredMatches = scoringService.score(context, validatedMatches);
        List<RankedMatchCandidate> rankedMatches = rankingService.rank(context, scoredMatches);

        List<RideMatchDto> matches = rankedMatches.stream()
                .map(RideMatchDto::from)
                .collect(Collectors.toList());

        return TripSearchResponse.builder()
                .passengerRoute(PassengerRouteDto.from(passengerRoute))
                .matches(matches)
                .build();
    }
}
