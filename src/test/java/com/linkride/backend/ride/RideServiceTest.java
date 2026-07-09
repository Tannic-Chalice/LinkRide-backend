package com.linkride.backend.ride;

import com.linkride.backend.entity.User;
import com.linkride.backend.entity.Vehicle;
import com.linkride.backend.location.GeoPointDto;
import com.linkride.backend.repository.UserRepository;
import com.linkride.backend.repository.VehicleRepository;
import com.linkride.backend.route.RouteFailureReason;
import com.linkride.backend.route.RouteGenerationState;
import com.linkride.backend.route.RouteProvider;
import com.linkride.backend.route.RouteResult;
import com.linkride.backend.route.geometry.RouteGeometryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RideServiceTest {

    @Mock
    private RideRepository rideRepository;
    @Mock
    private RideWaypointRepository rideWaypointRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private RouteProvider routeProvider;

    private RideService rideService;

    private UUID driverId;
    private User driver;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        // Real builder: it's pure decode/annotate logic, no dependencies — mocking it would just add noise.
        rideService = new RideService(rideRepository, rideWaypointRepository, userRepository, vehicleRepository,
                routeProvider, new RouteGeometryBuilder());

        driverId = UUID.randomUUID();
        driver = new User();
        driver.setId(driverId);

        vehicle = new Vehicle();
        vehicle.setNoOfSeats(4);
        vehicle.setIsActive(true);
        vehicle.setIsVerified(true);

        lenient().when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        lenient().when(routeProvider.computeRoute(any(), any(), any()))
                .thenReturn(RouteResult.pending(null));
        lenient().when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> {
            Ride r = inv.getArgument(0);
            r.setRideId(UUID.randomUUID());
            return r;
        });
    }

    private RideCreateRequest validRequest() {
        RideCreateRequest request = new RideCreateRequest();
        request.setPickup(geoPoint(12.9716, 77.5946));
        request.setDestination(geoPoint(13.1986, 77.7066));
        request.setDepartureTime(OffsetDateTime.now().plusHours(2));
        request.setOfferedSeats(3);
        return request;
    }

    private GeoPointDto geoPoint(double lat, double lng) {
        GeoPointDto dto = new GeoPointDto();
        dto.setName("Test Location");
        dto.setAddress("123 Test Street");
        dto.setLatitude(lat);
        dto.setLongitude(lng);
        return dto;
    }

    @Test
    void createRide_happyPath_setsBackendControlledFieldsFromVehicleAndRequest() {
        when(vehicleRepository.findByOwnerIdAndIsActiveTrueAndIsVerifiedTrue(driverId))
                .thenReturn(Optional.of(vehicle));

        RideResponse response = rideService.createRide(driverId, validRequest());

        assertThat(response.getTotalSeats()).isEqualTo(4);
        assertThat(response.getAvailableSeats()).isEqualTo(3);
        assertThat(response.getOfferedSeats()).isEqualTo(3);
        assertThat(response.getStatus()).isEqualTo(RideStatus.SCHEDULED);
        assertThat(response.getRoutePolyline()).isNull();
        assertThat(response.getRouteGenerationState()).isEqualTo(RouteGenerationState.PENDING);
    }

    @Test
    void createRide_routeUnroutable_stillPersistsRideWithUnroutableState() {
        when(vehicleRepository.findByOwnerIdAndIsActiveTrueAndIsVerifiedTrue(driverId))
                .thenReturn(Optional.of(vehicle));
        when(routeProvider.computeRoute(any(), any(), any()))
                .thenReturn(RouteResult.unroutable(RouteFailureReason.ZERO_RESULTS));

        RideResponse response = rideService.createRide(driverId, validRequest());

        assertThat(response.getRouteGenerationState()).isEqualTo(RouteGenerationState.UNROUTABLE);
        assertThat(response.getRoutePolyline()).isNull();
        assertThat(response.getStatus()).isEqualTo(RideStatus.SCHEDULED);

        ArgumentCaptor<Ride> rideCaptor = ArgumentCaptor.forClass(Ride.class);
        verify(rideRepository).save(rideCaptor.capture());
        assertThat(rideCaptor.getValue().getRouteGeometry()).isNull();
    }

    @Test
    void createRide_routeReady_persistsRouteData() {
        when(vehicleRepository.findByOwnerIdAndIsActiveTrueAndIsVerifiedTrue(driverId))
                .thenReturn(Optional.of(vehicle));
        // Google's own documented example polyline — a real encoded polyline is required now
        // that route creation also decodes it into RouteGeometry (Phase 2B.4).
        when(routeProvider.computeRoute(any(), any(), any()))
                .thenReturn(RouteResult.ready("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5000, 600));

        RideResponse response = rideService.createRide(driverId, validRequest());

        assertThat(response.getRouteGenerationState()).isEqualTo(RouteGenerationState.READY);
        assertThat(response.getRoutePolyline()).isEqualTo("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
        assertThat(response.getEstimatedDistanceMeters()).isEqualTo(5000);
        assertThat(response.getEstimatedDurationSeconds()).isEqualTo(600);
    }

    @Test
    void createRide_routeReady_populatesRouteGeometryOnPersistedEntity() {
        when(vehicleRepository.findByOwnerIdAndIsActiveTrueAndIsVerifiedTrue(driverId))
                .thenReturn(Optional.of(vehicle));
        // Google's own documented example polyline — enough to prove wiring end-to-end.
        when(routeProvider.computeRoute(any(), any(), any()))
                .thenReturn(RouteResult.ready("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5000, 600));

        rideService.createRide(driverId, validRequest());

        ArgumentCaptor<Ride> rideCaptor = ArgumentCaptor.forClass(Ride.class);
        verify(rideRepository).save(rideCaptor.capture());
        assertThat(rideCaptor.getValue().getRouteGeometry()).isNotNull();
        assertThat(rideCaptor.getValue().getRouteGeometry().totalDistanceMeters()).isEqualTo(5000);
    }

    @Test
    void createRide_driverNotFound_throws404() {
        when(userRepository.findById(driverId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rideService.createRide(driverId, validRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void createRide_noActiveVerifiedVehicle_throws403() {
        when(vehicleRepository.findByOwnerIdAndIsActiveTrueAndIsVerifiedTrue(driverId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> rideService.createRide(driverId, validRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("verified vehicle");
    }

    @Test
    void createRide_offeredSeatsExceedsCapacity_throwsValidationError() {
        when(vehicleRepository.findByOwnerIdAndIsActiveTrueAndIsVerifiedTrue(driverId))
                .thenReturn(Optional.of(vehicle));

        RideCreateRequest request = validRequest();
        request.setOfferedSeats(10); // vehicle only seats 4

        assertThatThrownBy(() -> rideService.createRide(driverId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds vehicle capacity");
    }

    @Test
    void createRide_pickupEqualsDestination_throwsValidationError() {
        when(vehicleRepository.findByOwnerIdAndIsActiveTrueAndIsVerifiedTrue(driverId))
                .thenReturn(Optional.of(vehicle));

        RideCreateRequest request = validRequest();
        request.setDestination(geoPoint(12.9716, 77.5946)); // same as pickup

        assertThatThrownBy(() -> rideService.createRide(driverId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be the same location");
    }

    @Test
    void createRide_withWaypoints_persistsInOrder() {
        when(vehicleRepository.findByOwnerIdAndIsActiveTrueAndIsVerifiedTrue(driverId))
                .thenReturn(Optional.of(vehicle));
        when(rideWaypointRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        RideCreateRequest request = validRequest();
        request.setWaypoints(List.of(geoPoint(12.99, 77.60), geoPoint(13.05, 77.65)));

        RideResponse response = rideService.createRide(driverId, request);

        assertThat(response.getWaypoints()).hasSize(2);
    }
}
