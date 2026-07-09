package com.linkride.backend.discovery;

import com.linkride.backend.discovery.corridor.CorridorGenerationService;
import com.linkride.backend.discovery.corridor.CorridorMatchCandidate;
import com.linkride.backend.ride.Ride;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Phase 2B.5 scope: validate the request, compute the passenger's own route, assemble the
 * {@link PassengerSearchContext}, run coarse candidate generation, run shared-corridor
 * computation, and map survivors to a match list. No hard-constraint validation (Phase 2B.6,
 * beyond what {@code CandidateGenerationService} already checks) and no scoring or ranking
 * (Phase 2B.7–2B.8) exist yet — see {@link RideMatchDto}'s Javadoc for exactly what that means
 * for the response. Every later stage slots into this same pipeline, all consuming the same
 * {@link PassengerSearchContext} rather than their own bespoke parameters.
 */
@Service
@RequiredArgsConstructor
public class TripSearchServiceImpl implements TripSearchService {

    private final TripSearchRequestValidator validator;
    private final PassengerRouteService passengerRouteService;
    private final CandidateGenerationService candidateGenerationService;
    private final CorridorGenerationService corridorGenerationService;

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

        List<RideMatchDto> matches = corridorMatches.stream()
                .map(RideMatchDto::from)
                .collect(Collectors.toList());

        return TripSearchResponse.builder()
                .passengerRoute(PassengerRouteDto.from(passengerRoute))
                .matches(matches)
                .build();
    }
}
