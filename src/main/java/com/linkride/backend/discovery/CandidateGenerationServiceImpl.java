package com.linkride.backend.discovery;

import com.linkride.backend.ride.Ride;
import com.linkride.backend.ride.RideRepository;
import com.linkride.backend.ride.RideStatus;
import com.linkride.backend.route.RouteGenerationState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Phase 2B.3: coarse, inexpensive metadata filtering only — seat capacity, successful routing,
 * an active status, a departure-time window, and excluding the passenger's own rides, all pushed
 * into a single indexed-column query on {@link RideRepository}. Deliberately does not decode
 * polylines, compute shared corridors, estimate pickup/drop points, or score rides — those are
 * Phase 2B.4 onward.
 *
 * <p>Coarse geographic (bounding-box) filtering from design doc §2.4 is not yet included here:
 * it needs decoded route geometry, which doesn't exist until Phase 2B.4. {@code passengerRoute}
 * is already threaded through {@link PassengerSearchContext} so that filter can be added here
 * without another signature change.</p>
 *
 * <p><b>Keep {@link RideRepository}'s query narrowly scoped to cheap, indexed-column SQL
 * predicates.</b> Business rules that aren't plain metadata — a driver having blocked this
 * passenger, this passenger already being accepted on the ride, the ride having filled up since
 * it was last read — don't belong in JPQL and shouldn't be bolted onto that query as they show
 * up. If/when those appear, they belong in a separate eligibility stage (e.g. a
 * {@code CandidateEligibilityService} or a small chain of post-fetch filters) sitting between
 * this repository call and the return below, not inside the SQL. Not needed yet — noted here so
 * it doesn't get discovered the hard way once the query is already overloaded.</p>
 */
@Service
@RequiredArgsConstructor
public class CandidateGenerationServiceImpl implements CandidateGenerationService {

    private final RideRepository rideRepository;
    private final MatchingProperties matchingProperties;

    @Override
    public List<Ride> generateCandidates(PassengerSearchContext context) {
        TripSearchRequest request = context.getRequest();
        OffsetDateTime desiredDeparture = request.getDesiredDepartureTime();
        int windowMinutes = matchingProperties.getDepartureWindowMinutes();

        return rideRepository.findCandidatesForSearch(
                request.getPassengerCount(),
                RouteGenerationState.READY,
                RideStatus.SCHEDULED,
                desiredDeparture.minusMinutes(windowMinutes),
                desiredDeparture.plusMinutes(windowMinutes),
                context.getPassengerId());
    }
}
