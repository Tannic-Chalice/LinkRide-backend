package com.linkride.backend.discovery.validation;

import com.linkride.backend.discovery.MatchingProperties;
import com.linkride.backend.discovery.PassengerSearchContext;
import com.linkride.backend.discovery.corridor.CorridorMatchCandidate;
import com.linkride.backend.ride.Ride;
import com.linkride.backend.route.geometry.NearestPointOnRoute;
import com.linkride.backend.route.geometry.RouteEtaCalculator;
import com.linkride.backend.route.geometry.RouteGeometry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 2B.6: the hard-constraint gate from design doc §5.4, run once per surviving corridor run
 * (not once per generated pair, since the corridor computation in Phase 2B.5 already did the
 * geometric filtering). Every check here is binary — no soft penalties, no scoring (that's
 * §6 / Phase 2B.7) — a candidate either passes all of them or is dropped outright.
 *
 * <p>Checks, in the order the design doc's §5.4 table lists them:</p>
 * <ul>
 *   <li><b>Drop after pickup</b> — guaranteed by construction (corridor entry precedes exit in
 *   passenger-route order, and both project forward onto the driver's route), but asserted
 *   defensively here per the doc's own note rather than trusted blindly.</li>
 *   <li><b>Drop before/at driver's destination</b> — likewise structurally guaranteed by
 *   {@link RouteGeometry#nearestPoint}, which can never project past the end of the driver's
 *   route, but checked explicitly for the same defensive reason.</li>
 *   <li><b>Seat capacity</b> — re-checked here even though {@code CandidateGenerationServiceImpl}
 *   already filtered on it, since seat availability isn't otherwise guaranteed to still hold by
 *   the time this stage runs and the check is essentially free.</li>
 *   <li><b>Time compatibility</b> — the substantive check: the driver's estimated arrival at the
 *   derived pickup point, not just the ride's overall departure time (which the coarse filter in
 *   {@code CandidateGenerationServiceImpl} already bounds loosely), must fall within
 *   {@link MatchingProperties#getPickupTimeToleranceMinutes()} of the passenger's desired
 *   departure time.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MatchValidationServiceImpl implements MatchValidationService {

    private final MatchingProperties matchingProperties;

    @Override
    public List<CorridorMatchCandidate> validate(PassengerSearchContext context, List<CorridorMatchCandidate> candidates) {
        int passengerCount = context.getRequest().getPassengerCount();
        OffsetDateTime desiredDeparture = context.getRequest().getDesiredDepartureTime();

        List<CorridorMatchCandidate> results = new ArrayList<>();
        for (CorridorMatchCandidate candidate : candidates) {
            if (isValid(candidate, passengerCount, desiredDeparture)) {
                results.add(candidate);
            }
        }
        return results;
    }

    private boolean isValid(CorridorMatchCandidate candidate, int passengerCount, OffsetDateTime desiredDeparture) {
        Ride ride = candidate.ride();
        RouteGeometry driverGeometry = ride.getRouteGeometry();
        NearestPointOnRoute pickup = candidate.pickup();
        NearestPointOnRoute drop = candidate.drop();

        if (drop.cumulativeDistanceMeters() <= pickup.cumulativeDistanceMeters()) {
            return false;
        }
        if (drop.cumulativeDistanceMeters() > driverGeometry.totalDistanceMeters()) {
            return false;
        }
        if (ride.getAvailableSeats() < passengerCount) {
            return false;
        }

        return withinPickupTimeWindow(ride, driverGeometry, pickup, desiredDeparture);
    }

    private boolean withinPickupTimeWindow(
            Ride ride, RouteGeometry driverGeometry, NearestPointOnRoute pickup, OffsetDateTime desiredDeparture) {
        OffsetDateTime driverEtaAtPickup = RouteEtaCalculator.etaAt(
                ride.getDepartureTime(), driverGeometry, pickup.cumulativeDistanceMeters());
        long diffMinutes = Math.abs(Duration.between(desiredDeparture, driverEtaAtPickup).toMinutes());
        return diffMinutes <= matchingProperties.getPickupTimeToleranceMinutes();
    }
}
