package com.linkride.backend.discovery.scoring;

import com.linkride.backend.discovery.MatchingProperties;
import com.linkride.backend.discovery.PassengerSearchContext;
import com.linkride.backend.discovery.TripSearchRequest;
import com.linkride.backend.discovery.corridor.CorridorMatchCandidate;
import com.linkride.backend.discovery.corridor.CorridorRun;
import com.linkride.backend.location.GeoPointDto;
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

class ScoringServiceImplTest {

    private static final OffsetDateTime DEPARTURE = OffsetDateTime.parse("2026-08-01T09:00:00Z");

    private MatchingProperties matchingProperties;
    private ScoringServiceImpl scoringService;

    @BeforeEach
    void setUp() {
        matchingProperties = new MatchingProperties();
        scoringService = new ScoringServiceImpl(matchingProperties);
    }

    private GeoPointDto point(double lat, double lng) {
        GeoPointDto dto = new GeoPointDto();
        dto.setName("p");
        dto.setAddress("a");
        dto.setLatitude(lat);
        dto.setLongitude(lng);
        return dto;
    }

    private Ride ride() {
        RoutePoint start = new RoutePoint(12.9716, 77.5900, 0, 0, 0);
        RoutePoint end = new RoutePoint(12.9716, 77.6300, 1000, 600, 1);
        RouteGeometry geometry = new RouteGeometry(List.of(start, end), 1000, 600, BoundingBox.of(List.of()));

        Ride ride = new Ride();
        ride.setRideId(UUID.randomUUID());
        ride.setDepartureTime(DEPARTURE);
        ride.setRouteGeometry(geometry);
        return ride;
    }

    private CorridorMatchCandidate candidate(
            Ride ride, double pickupLat, double pickupLng, double pickupCumDist,
            double dropLat, double dropLng, double dropCumDist) {
        RoutePoint entry = new RoutePoint(pickupLat, pickupLng, 0, 0, 0);
        RoutePoint exit = new RoutePoint(dropLat, dropLng, 500, 50, 1);
        CorridorRun run = new CorridorRun(entry, exit, 0.5);
        NearestPointOnRoute pickup = new NearestPointOnRoute(pickupLat, pickupLng, pickupCumDist, 10);
        NearestPointOnRoute drop = new NearestPointOnRoute(dropLat, dropLng, dropCumDist, 10);
        return new CorridorMatchCandidate(ride, run, pickup, drop);
    }

    private PassengerSearchContext context(double originLat, double originLng, double destLat, double destLng) {
        TripSearchRequest request = new TripSearchRequest();
        request.setOrigin(point(originLat, originLng));
        request.setDestination(point(destLat, destLng));
        request.setPassengerCount(1);
        request.setDesiredDepartureTime(DEPARTURE);
        return PassengerSearchContext.builder()
                .passengerId(UUID.randomUUID())
                .request(request)
                .build();
    }

    @Test
    void score_emptyCandidates_returnsEmpty() {
        List<ScoredMatchCandidate> result = scoringService.score(
                context(12.9716, 77.5900, 12.9716, 77.6300), List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void score_passengerDetourRatioExceedsCeiling_rejected() {
        matchingProperties.setMaxPassengerDetourRatio(1.4);
        Ride ride = ride();
        // Origin/destination close together (short direct distance), pickup/drop far from both
        // (large walk) -> passengerDetourRatio blows past the ceiling.
        CorridorMatchCandidate candidate = candidate(ride, 12.9716, 77.5900, 200, 13.5000, 78.5000, 800);

        List<ScoredMatchCandidate> result = scoringService.score(
                context(12.9716, 77.5900, 12.9716, 77.5901), List.of(candidate));

        assertThat(result).isEmpty();
    }

    @Test
    void score_singleCandidate_normalizesToZeroCompositeScore() {
        Ride ride = ride();
        CorridorMatchCandidate candidate = candidate(ride, 12.9716, 77.5950, 200, 12.9716, 77.6250, 800);

        List<ScoredMatchCandidate> result = scoringService.score(
                context(12.9716, 77.5900, 12.9716, 77.6300), List.of(candidate));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).score().compositeScore()).isEqualTo(0.0);
    }

    @Test
    void score_driverDetourDistanceAlwaysZero() {
        Ride ride = ride();
        CorridorMatchCandidate candidate = candidate(ride, 12.9716, 77.5950, 200, 12.9716, 77.6250, 800);

        List<ScoredMatchCandidate> result = scoringService.score(
                context(12.9716, 77.5900, 12.9716, 77.6300), List.of(candidate));

        assertThat(result.get(0).score().driverDetourDistanceMeters()).isEqualTo(0.0);
        assertThat(result.get(0).score().driverDetourTimeSeconds())
                .isEqualTo(2.0 * matchingProperties.getStopTimeSeconds());
    }

    @Test
    void score_lowerWalkAndDetourRatio_scoresBetterThanHigherWalkCandidate() {
        Ride ride = ride();
        // Same pickup/drop progress along the route (same cumulative distance) for both, so
        // ridePortionDistance, benefitRatio, and the driver-ETA-based time-window offset are
        // identical between the two candidates — only the walk legs differ.
        CorridorMatchCandidate closeWalk = candidate(ride, 12.9716, 77.5901, 200, 12.9716, 77.6299, 800);
        CorridorMatchCandidate farWalk = candidate(ride, 12.9750, 77.6000, 200, 12.9680, 77.6150, 800);

        List<ScoredMatchCandidate> result = scoringService.score(
                context(12.9716, 77.5900, 12.9716, 77.6300), List.of(closeWalk, farWalk));

        assertThat(result).hasSize(2);
        ScoredMatchCandidate closeResult = result.stream()
                .filter(r -> r.match() == closeWalk).findFirst().orElseThrow();
        ScoredMatchCandidate farResult = result.stream()
                .filter(r -> r.match() == farWalk).findFirst().orElseThrow();

        assertThat(closeResult.score().compositeScore()).isLessThan(farResult.score().compositeScore());
        assertThat(closeResult.score().walkFraction()).isLessThan(farResult.score().walkFraction());
        assertThat(closeResult.score().passengerDetourRatio()).isLessThan(farResult.score().passengerDetourRatio());
        assertThat(closeResult.score().benefitRatio()).isEqualTo(farResult.score().benefitRatio());
        assertThat(closeResult.score().timeWindowOffsetMinutes()).isEqualTo(farResult.score().timeWindowOffsetMinutes());
    }
}
