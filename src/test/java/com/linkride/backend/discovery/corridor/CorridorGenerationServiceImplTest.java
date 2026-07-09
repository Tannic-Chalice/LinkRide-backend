package com.linkride.backend.discovery.corridor;

import com.linkride.backend.discovery.MatchingProperties;
import com.linkride.backend.discovery.PassengerRoute;
import com.linkride.backend.discovery.PassengerSearchContext;
import com.linkride.backend.discovery.TripSearchRequest;
import com.linkride.backend.ride.Ride;
import com.linkride.backend.route.geometry.CumulativeMetricsCalculator;
import com.linkride.backend.route.geometry.LatLng;
import com.linkride.backend.route.geometry.RouteGeometry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CorridorGenerationServiceImplTest {

    private MatchingProperties matchingProperties;
    private CorridorGenerationServiceImpl corridorGenerationService;

    @BeforeEach
    void setUp() {
        matchingProperties = new MatchingProperties();
        matchingProperties.setCorridorWalkToleranceMeters(750);
        matchingProperties.setMinOverlapFraction(0.15);
        corridorGenerationService = new CorridorGenerationServiceImpl(matchingProperties);
    }

    private RouteGeometry straightRoute(double lat, double startLng, double endLng, int numPoints) {
        List<LatLng> points = new ArrayList<>();
        for (int i = 0; i < numPoints; i++) {
            double t = (double) i / (numPoints - 1);
            points.add(new LatLng(lat, startLng + t * (endLng - startLng)));
        }
        return CumulativeMetricsCalculator.annotate(points, 1000, 100);
    }

    private Ride rideWithGeometry(RouteGeometry geometry) {
        Ride ride = new Ride();
        ride.setRideId(UUID.randomUUID());
        ride.setRouteGeometry(geometry);
        return ride;
    }

    private PassengerSearchContext context(PassengerRoute passengerRoute, TripSearchRequest request) {
        return PassengerSearchContext.builder()
                .passengerId(UUID.randomUUID())
                .request(request)
                .passengerRoute(passengerRoute)
                .build();
    }

    private TripSearchRequest requestWithNoOverrides() {
        return new TripSearchRequest();
    }

    @Test
    void generateCorridors_passengerGeometryNull_returnsEmptyImmediately() {
        PassengerRoute passengerRoute = PassengerRoute.builder().geometry(null).build();
        Ride ride = rideWithGeometry(straightRoute(12.9716, 77.5900, 77.6100, 20));

        List<CorridorMatchCandidate> result = corridorGenerationService.generateCorridors(
                context(passengerRoute, requestWithNoOverrides()), List.of(ride));

        assertThat(result).isEmpty();
    }

    @Test
    void generateCorridors_rideWithNullGeometry_isSkipped() {
        RouteGeometry passengerGeometry = straightRoute(12.9716, 77.5900, 77.6100, 20);
        PassengerRoute passengerRoute = PassengerRoute.builder().geometry(passengerGeometry).build();
        Ride ride = new Ride();
        ride.setRideId(UUID.randomUUID());
        ride.setRouteGeometry(null);

        List<CorridorMatchCandidate> result = corridorGenerationService.generateCorridors(
                context(passengerRoute, requestWithNoOverrides()), List.of(ride));

        assertThat(result).isEmpty();
    }

    @Test
    void generateCorridors_boundingBoxesFarApart_rideExcludedByFirstStageRejection() {
        RouteGeometry passengerGeometry = straightRoute(12.9716, 77.5900, 77.6100, 20);
        PassengerRoute passengerRoute = PassengerRoute.builder().geometry(passengerGeometry).build();
        Ride ride = rideWithGeometry(straightRoute(20.0000, 78.0000, 78.0200, 20));

        List<CorridorMatchCandidate> result = corridorGenerationService.generateCorridors(
                context(passengerRoute, requestWithNoOverrides()), List.of(ride));

        assertThat(result).isEmpty();
    }

    @Test
    void generateCorridors_overlapBelowMinFraction_rideExcluded() {
        RouteGeometry driverGeometry = straightRoute(12.9716, 77.5900, 77.6100, 20);

        List<LatLng> passengerPoints = new ArrayList<>();
        int n = 50;
        for (int i = 0; i < n; i++) {
            double t = (double) i / (n - 1);
            passengerPoints.add(new LatLng(12.9600 + t * (12.9832 - 12.9600), 77.6000));
        }
        RouteGeometry passengerGeometry = CumulativeMetricsCalculator.annotate(passengerPoints, 1000, 100);
        PassengerRoute passengerRoute = PassengerRoute.builder().geometry(passengerGeometry).build();

        matchingProperties.setCorridorWalkToleranceMeters(100);
        Ride ride = rideWithGeometry(driverGeometry);

        List<CorridorMatchCandidate> result = corridorGenerationService.generateCorridors(
                context(passengerRoute, requestWithNoOverrides()), List.of(ride));

        assertThat(result).isEmpty();
    }

    @Test
    void generateCorridors_overlapAboveMinFraction_includesRideWithPickupAndDrop() {
        RouteGeometry sharedGeometry = straightRoute(12.9716, 77.5900, 77.6100, 20);
        PassengerRoute passengerRoute = PassengerRoute.builder().geometry(sharedGeometry).build();
        Ride ride = rideWithGeometry(straightRoute(12.9716, 77.5900, 77.6100, 20));

        List<CorridorMatchCandidate> result = corridorGenerationService.generateCorridors(
                context(passengerRoute, requestWithNoOverrides()), List.of(ride));

        assertThat(result).hasSize(1);
        CorridorMatchCandidate match = result.get(0);
        assertThat(match.ride()).isEqualTo(ride);
        assertThat(match.pickup().lat()).isCloseTo(12.9716, within(1e-4));
        assertThat(match.drop().lat()).isCloseTo(12.9716, within(1e-4));
    }

    @Test
    void generateCorridors_requestOverridesWalkTolerance_usesLargerOfPickupAndDropOverrides() {
        // Driver route offset ~1000m north of the passenger's route: beyond the 750m default,
        // within an overridden 1200m tolerance.
        RouteGeometry passengerGeometry = straightRoute(12.9716, 77.5900, 77.6100, 20);
        RouteGeometry driverGeometry = straightRoute(12.9806, 77.5900, 77.6100, 20);
        PassengerRoute passengerRoute = PassengerRoute.builder().geometry(passengerGeometry).build();
        Ride ride = rideWithGeometry(driverGeometry);

        TripSearchRequest defaultRequest = requestWithNoOverrides();
        List<CorridorMatchCandidate> withDefaultTolerance = corridorGenerationService.generateCorridors(
                context(passengerRoute, defaultRequest), List.of(ride));
        assertThat(withDefaultTolerance).isEmpty();

        TripSearchRequest overriddenRequest = new TripSearchRequest();
        overriddenRequest.setMaxWalkToPickupMeters(1200);
        overriddenRequest.setMaxWalkFromDropMeters(200); // smaller — the larger of the two should win
        List<CorridorMatchCandidate> withOverriddenTolerance = corridorGenerationService.generateCorridors(
                context(passengerRoute, overriddenRequest), List.of(ride));
        assertThat(withOverriddenTolerance).hasSize(1);
    }
}
