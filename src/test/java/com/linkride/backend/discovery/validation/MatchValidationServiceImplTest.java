package com.linkride.backend.discovery.validation;

import com.linkride.backend.discovery.MatchingProperties;
import com.linkride.backend.discovery.PassengerSearchContext;
import com.linkride.backend.discovery.TripSearchRequest;
import com.linkride.backend.discovery.corridor.CorridorMatchCandidate;
import com.linkride.backend.discovery.corridor.CorridorRun;
import com.linkride.backend.ride.Ride;
import com.linkride.backend.route.geometry.BoundingBox;
import com.linkride.backend.route.geometry.NearestPointOnRoute;
import com.linkride.backend.route.geometry.RouteGeometry;
import com.linkride.backend.route.geometry.RoutePoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MatchValidationServiceImplTest {

    private static final OffsetDateTime DEPARTURE = OffsetDateTime.parse("2026-08-01T09:00:00Z");

    private MatchingProperties matchingProperties;
    private MatchValidationServiceImpl matchValidationService;

    @BeforeEach
    void setUp() {
        matchingProperties = new MatchingProperties();
        matchingProperties.setPickupTimeToleranceMinutes(30);
        matchValidationService = new MatchValidationServiceImpl(matchingProperties);
    }

    private RouteGeometry driverGeometry(double totalDistanceMeters, double totalDurationSeconds) {
        RoutePoint start = new RoutePoint(12.9716, 77.5900, 0, 0, 0);
        RoutePoint end = new RoutePoint(12.9716, 77.6100, totalDistanceMeters, totalDurationSeconds, 1);
        return new RouteGeometry(List.of(start, end), totalDistanceMeters, totalDurationSeconds, BoundingBox.of(List.of()));
    }

    private Ride ride(double totalDistanceMeters, double totalDurationSeconds, int availableSeats) {
        Ride ride = new Ride();
        ride.setRideId(UUID.randomUUID());
        ride.setDepartureTime(DEPARTURE);
        ride.setAvailableSeats(availableSeats);
        ride.setRouteGeometry(driverGeometry(totalDistanceMeters, totalDurationSeconds));
        return ride;
    }

    private CorridorMatchCandidate candidate(Ride ride, double pickupCumDist, double dropCumDist) {
        RoutePoint entry = new RoutePoint(12.9716, 77.5950, 0, 0, 0);
        RoutePoint exit = new RoutePoint(12.9716, 77.6050, 500, 50, 1);
        CorridorRun run = new CorridorRun(entry, exit, 0.5);
        NearestPointOnRoute pickup = new NearestPointOnRoute(12.9716, 77.5950, pickupCumDist, 10);
        NearestPointOnRoute drop = new NearestPointOnRoute(12.9716, 77.6050, dropCumDist, 10);
        return new CorridorMatchCandidate(ride, run, pickup, drop);
    }

    private PassengerSearchContext context(int passengerCount, OffsetDateTime desiredDeparture) {
        TripSearchRequest request = new TripSearchRequest();
        request.setPassengerCount(passengerCount);
        request.setDesiredDepartureTime(desiredDeparture);
        return PassengerSearchContext.builder()
                .passengerId(UUID.randomUUID())
                .request(request)
                .build();
    }

    @Test
    void validate_dropBeforeOrAtPickup_rejected() {
        Ride ride = ride(1000, 600, 4);
        CorridorMatchCandidate candidate = candidate(ride, 500, 500);

        List<CorridorMatchCandidate> result = matchValidationService.validate(
                context(1, DEPARTURE), List.of(candidate));

        assertThat(result).isEmpty();
    }

    @Test
    void validate_dropBeyondDriverDestination_rejected() {
        Ride ride = ride(1000, 600, 4);
        CorridorMatchCandidate candidate = candidate(ride, 200, 1500);

        List<CorridorMatchCandidate> result = matchValidationService.validate(
                context(1, DEPARTURE), List.of(candidate));

        assertThat(result).isEmpty();
    }

    @Test
    void validate_insufficientSeats_rejected() {
        Ride ride = ride(1000, 600, 1);
        CorridorMatchCandidate candidate = candidate(ride, 200, 800);

        List<CorridorMatchCandidate> result = matchValidationService.validate(
                context(2, DEPARTURE), List.of(candidate));

        assertThat(result).isEmpty();
    }

    @Test
    void validate_pickupEtaFarFromDesiredDeparture_rejected() {
        // 1000m / 600s route, pickup at 500m -> driver ETA at pickup is departure + 5 minutes.
        Ride ride = ride(1000, 600, 4);
        CorridorMatchCandidate candidate = candidate(ride, 500, 800);

        List<CorridorMatchCandidate> result = matchValidationService.validate(
                context(1, DEPARTURE.plusMinutes(60)), List.of(candidate));

        assertThat(result).isEmpty();
    }

    @Test
    void validate_pickupEtaWithinTolerance_kept() {
        Ride ride = ride(1000, 600, 4);
        CorridorMatchCandidate candidate = candidate(ride, 500, 800);

        List<CorridorMatchCandidate> result = matchValidationService.validate(
                context(1, DEPARTURE.plusMinutes(5)), List.of(candidate));

        assertThat(result).containsExactly(candidate);
    }

    @Test
    void validate_mixedCandidates_returnsOnlyValidOnes() {
        Ride validRide = ride(1000, 600, 4);
        Ride outOfSeatsRide = ride(1000, 600, 0);
        CorridorMatchCandidate valid = candidate(validRide, 500, 800);
        CorridorMatchCandidate invalid = candidate(outOfSeatsRide, 500, 800);

        List<CorridorMatchCandidate> result = matchValidationService.validate(
                context(1, DEPARTURE.plusMinutes(5)), List.of(valid, invalid));

        assertThat(result).containsExactly(valid);
    }
}
