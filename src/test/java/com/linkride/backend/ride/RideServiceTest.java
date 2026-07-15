package com.linkride.backend.ride;

import com.linkride.backend.booking.BookingRepository;
import com.linkride.backend.entity.User;
import com.linkride.backend.entity.Vehicle;
import com.linkride.backend.location.GeoPoint;
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
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
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
import static org.mockito.Mockito.never;
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
    @Mock
    private BookingRepository bookingRepository;

    private RideService rideService;

    private UUID driverId;
    private User driver;
    private Vehicle vehicle;
    private UUID rideId;
    private Ride ride;

    @BeforeEach
    void setUp() {
        // Real builder: it's pure decode/annotate logic, no dependencies — mocking it would just add noise.
        rideService = new RideService(rideRepository, rideWaypointRepository, userRepository, vehicleRepository,
                routeProvider, new RouteGeometryBuilder(), bookingRepository);

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

        rideId = UUID.randomUUID();
        ride = new Ride();
        ride.setRideId(rideId);
        ride.setDriver(driver);
        ride.setVehicle(vehicle);
        ride.setStatus(RideStatus.SCHEDULED);
        ride.setPickup(geoPointEntity(12.9716, 77.5946));
        ride.setDestination(geoPointEntity(13.1986, 77.7066));

        lenient().when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        lenient().when(rideWaypointRepository.findByRide_RideIdOrderBySequenceAsc(rideId)).thenReturn(List.of());
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

    private GeoPoint geoPointEntity(double lat, double lng) {
        GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);
        return new GeoPoint("Test Location", "123 Test Street", factory.createPoint(new Coordinate(lng, lat)));
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

    @Test
    void cancelRide_happyPath_cascadesToActiveBookings() {
        when(rideRepository.cancelIfScheduled(rideId)).thenReturn(1);

        RideResponse response = rideService.cancelRide(driverId, rideId);

        assertThat(response.getStatus()).isEqualTo(RideStatus.CANCELLED);
        verify(bookingRepository).cancelAllActiveBookingsForRide(rideId);
    }

    @Test
    void cancelRide_notTheDriver_throws403() {
        UUID strangerId = UUID.randomUUID();

        assertThatThrownBy(() -> rideService.cancelRide(strangerId, rideId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("do not drive");

        verify(rideRepository, never()).cancelIfScheduled(any(UUID.class));
    }

    @Test
    void cancelRide_notFound_throws404() {
        UUID missingRideId = UUID.randomUUID();
        when(rideRepository.findById(missingRideId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rideService.cancelRide(driverId, missingRideId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ride not found");
    }

    @Test
    void cancelRide_notScheduled_throws409() {
        // The atomic UPDATE's WHERE clause is the actual guard — 0 rows means the ride wasn't
        // SCHEDULED, whatever the in-memory entity happens to say.
        when(rideRepository.cancelIfScheduled(rideId)).thenReturn(0);

        assertThatThrownBy(() -> rideService.cancelRide(driverId, rideId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot be cancelled");

        verify(bookingRepository, never()).cancelAllActiveBookingsForRide(any(UUID.class));
    }

    @Test
    void startRide_happyPath_expiresStalePendingBookings() {
        when(rideRepository.startIfScheduled(rideId)).thenReturn(1);

        RideResponse response = rideService.startRide(driverId, rideId);

        assertThat(response.getStatus()).isEqualTo(RideStatus.IN_PROGRESS);
        verify(bookingRepository).expireAllPendingBookingsForRide(rideId);
    }

    @Test
    void startRide_notTheDriver_throws403() {
        UUID strangerId = UUID.randomUUID();

        assertThatThrownBy(() -> rideService.startRide(strangerId, rideId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("do not drive");

        verify(rideRepository, never()).startIfScheduled(any(UUID.class));
    }

    @Test
    void startRide_notScheduled_throws409() {
        when(rideRepository.startIfScheduled(rideId)).thenReturn(0);

        assertThatThrownBy(() -> rideService.startRide(driverId, rideId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot be started");

        verify(bookingRepository, never()).expireAllPendingBookingsForRide(any(UUID.class));
    }

    @Test
    void completeRide_happyPath_completesAcceptedBookings() {
        when(rideRepository.completeIfInProgress(rideId)).thenReturn(1);

        RideResponse response = rideService.completeRide(driverId, rideId);

        assertThat(response.getStatus()).isEqualTo(RideStatus.COMPLETED);
        verify(bookingRepository).completeAllAcceptedBookingsForRide(rideId);
    }

    @Test
    void completeRide_notTheDriver_throws403() {
        UUID strangerId = UUID.randomUUID();

        assertThatThrownBy(() -> rideService.completeRide(strangerId, rideId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("do not drive");

        verify(rideRepository, never()).completeIfInProgress(any(UUID.class));
    }

    @Test
    void completeRide_notInProgress_throws409() {
        when(rideRepository.completeIfInProgress(rideId)).thenReturn(0);

        assertThatThrownBy(() -> rideService.completeRide(driverId, rideId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot be completed");

        verify(bookingRepository, never()).completeAllAcceptedBookingsForRide(any(UUID.class));
    }
}
