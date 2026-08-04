package com.linkride.backend.tracking;

import com.linkride.backend.booking.Booking;
import com.linkride.backend.booking.BookingRepository;
import com.linkride.backend.booking.BookingStatus;
import com.linkride.backend.entity.User;
import com.linkride.backend.ride.Ride;
import com.linkride.backend.ride.RideRepository;
import com.linkride.backend.ride.RideStatus;
import com.linkride.backend.ride.RideWaypointRepository;
import com.linkride.backend.route.geometry.BoundingBox;
import com.linkride.backend.route.geometry.LatLng;
import com.linkride.backend.route.geometry.RouteGeometry;
import com.linkride.backend.route.geometry.RoutePoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

/**
 * Mockito-based service test — this codebase has no {@code @WebMvcTest}/MockMvc precedent to
 * mirror for a controller-slice test (§15 calls for one), so this covers the same ground at the
 * service layer instead, the same style {@code BoardingServiceImplTest} already uses.
 */
@ExtendWith(MockitoExtension.class)
class LocationUpdateServiceTest {

    @Mock
    private RideRepository rideRepository;
    @Mock
    private RideWaypointRepository rideWaypointRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private LiveRideStateStore liveRideStateStore;
    @Mock
    private GpsFixValidator gpsFixValidator;
    @Mock
    private RouteProgressEngine routeProgressEngine;
    @Mock
    private EtaEngine etaEngine;
    @Mock
    private ArrivalDetectionService arrivalDetectionService;
    @Mock
    private LiveTrackingBroadcaster liveTrackingBroadcaster;

    private LocationUpdateService service;

    private UUID driverId;
    private UUID rideId;
    private Ride ride;
    private RouteGeometry geometry;

    @BeforeEach
    void setUp() {
        service = new LocationUpdateService(
                rideRepository, rideWaypointRepository, bookingRepository,
                liveRideStateStore, gpsFixValidator, routeProgressEngine, etaEngine, arrivalDetectionService,
                liveTrackingBroadcaster);

        driverId = UUID.randomUUID();
        rideId = UUID.randomUUID();

        User driver = new User();
        driver.setId(driverId);

        geometry = new RouteGeometry(
                List.of(new RoutePoint(12.9716, 77.5990, 0, 0, 0), new RoutePoint(12.9716, 77.6090, 1000, 1000, 1)),
                1000, 1000, BoundingBox.of(List.of(new LatLng(12.9716, 77.5990), new LatLng(12.9716, 77.6090))));

        ride = new Ride();
        ride.setRideId(rideId);
        ride.setDriver(driver);
        ride.setStatus(RideStatus.IN_PROGRESS);
        ride.setRouteGeometry(geometry);

        lenient().when(rideWaypointRepository.findByRide_RideIdOrderBySequenceAsc(rideId)).thenReturn(List.of());
        lenient().when(bookingRepository.findBoardingCandidatesForRide(rideId)).thenReturn(List.of());
        lenient().when(routeProgressEngine.computeProgress(any(), anyDouble(), anyDouble(), any()))
                .thenReturn(new RouteProgress(500, 500, 500, 0, true, List.of()));
    }

    private LocationUpdateRequest request(double lat, double lng, Instant recordedAt) {
        LocationUpdateRequest request = new LocationUpdateRequest();
        request.setLatitude(lat);
        request.setLongitude(lng);
        request.setRecordedAt(recordedAt);
        return request;
    }

    @Test
    void updateLocation_rideNotFound_throwsNotFound() {
        when(rideRepository.findById(rideId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateLocation(driverId, rideId, request(12.9716, 77.604, Instant.now())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void updateLocation_callerIsNotTheDriver_throwsForbidden() {
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> service.updateLocation(
                UUID.randomUUID(), rideId, request(12.9716, 77.604, Instant.now())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void updateLocation_rideNotInProgress_throwsConflict() {
        ride.setStatus(RideStatus.SCHEDULED);
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> service.updateLocation(driverId, rideId, request(12.9716, 77.604, Instant.now())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void updateLocation_accepted_savesStateAndReturnsFreshSnapshot() {
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(liveRideStateStore.get(rideId)).thenReturn(Optional.empty());
        when(gpsFixValidator.validate(any(), any(), any())).thenReturn(GpsValidationOutcome.ACCEPTED);

        Instant recordedAt = Instant.now();
        RideProgressSnapshot snapshot = service.updateLocation(driverId, rideId, request(12.9716, 77.604, recordedAt));

        verify(liveRideStateStore).save(eq(rideId), any());
        assertThat(snapshot.rideId()).isEqualTo(rideId);
        assertThat(snapshot.lastLatitude()).isEqualTo(12.9716);
        assertThat(snapshot.lastUpdatedAt()).isEqualTo(recordedAt);
    }

    @Test
    void updateLocation_ignoredDuplicate_returnsLastKnownSnapshotWithoutSaving() {
        LiveRideState lastKnown = new LiveRideState(rideId, 12.9716, 77.602, Instant.now(), new java.util.HashMap<>());
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(liveRideStateStore.get(rideId)).thenReturn(Optional.of(lastKnown));
        when(gpsFixValidator.validate(any(), any(), any())).thenReturn(GpsValidationOutcome.IGNORED_DUPLICATE);

        RideProgressSnapshot snapshot = service.updateLocation(driverId, rideId, request(12.9716, 77.602, Instant.now()));

        verify(liveRideStateStore, never()).save(any(), any());
        verify(arrivalDetectionService, never()).evaluate(any(), any(), any(), any());
        assertThat(snapshot.lastLatitude()).isEqualTo(lastKnown.getLastLatitude());
    }

    @Test
    void updateLocation_accepted_runsArrivalDetectionBeforeSavingState() {
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(liveRideStateStore.get(rideId)).thenReturn(Optional.empty());
        when(gpsFixValidator.validate(any(), any(), any())).thenReturn(GpsValidationOutcome.ACCEPTED);

        service.updateLocation(driverId, rideId, request(12.9716, 77.604, Instant.now()));

        verify(arrivalDetectionService).evaluate(eq(geometry), any(), eq(List.of()), any());
        verify(liveRideStateStore).save(eq(rideId), any());
    }

    @Test
    void updateLocation_accepted_reflectsArrivalStateAdvancedByDetectionService() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = new Booking();
        booking.setBookingId(bookingId);
        booking.setStatus(BookingStatus.ACCEPTED);

        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(liveRideStateStore.get(rideId)).thenReturn(Optional.empty());
        when(gpsFixValidator.validate(any(), any(), any())).thenReturn(GpsValidationOutcome.ACCEPTED);
        when(bookingRepository.findBoardingCandidatesForRide(rideId)).thenReturn(List.of(booking));
        when(etaEngine.etaForBooking(any(), any(), eq(booking), any())).thenReturn(
                Optional.of(new PassengerEtaView(bookingId, StopType.PICKUP, Instant.now(), ArrivalState.EN_ROUTE)));

        // Simulate ArrivalDetectionService advancing the booking to APPROACHING in place on the state it's handed.
        doAnswer(invocation -> {
            LiveRideState state = invocation.getArgument(3);
            state.getArrivalHighWaterMark().put(bookingId, new java.util.EnumMap<>(java.util.Map.of(StopType.PICKUP, ArrivalState.APPROACHING)));
            return java.util.Map.of(bookingId, ArrivalState.APPROACHING);
        }).when(arrivalDetectionService).evaluate(any(), any(), any(), any());

        RideProgressSnapshot snapshot = service.updateLocation(driverId, rideId, request(12.9716, 77.604, Instant.now()));

        assertThat(snapshot.passengerEtas()).hasSize(1);
        assertThat(snapshot.passengerEtas().get(0).arrivalState()).isEqualTo(ArrivalState.APPROACHING);
    }

    @Test
    void updateLocation_accepted_broadcastsSnapshotToDriver() {
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(liveRideStateStore.get(rideId)).thenReturn(Optional.empty());
        when(gpsFixValidator.validate(any(), any(), any())).thenReturn(GpsValidationOutcome.ACCEPTED);

        RideProgressSnapshot snapshot = service.updateLocation(driverId, rideId, request(12.9716, 77.604, Instant.now()));

        verify(liveTrackingBroadcaster).pushToUser(driverId, snapshot);
    }

    @Test
    void updateLocation_accepted_broadcastsPassengerEtaOnlyToCheckedInPassengers() {
        UUID checkedInBookingId = UUID.randomUUID();
        UUID checkedInPassengerId = UUID.randomUUID();
        User checkedInPassenger = new User();
        checkedInPassenger.setId(checkedInPassengerId);
        Booking checkedInBooking = new Booking();
        checkedInBooking.setBookingId(checkedInBookingId);
        checkedInBooking.setStatus(BookingStatus.CHECKED_IN);
        checkedInBooking.setPassenger(checkedInPassenger);

        UUID acceptedBookingId = UUID.randomUUID();
        Booking acceptedBooking = new Booking();
        acceptedBooking.setBookingId(acceptedBookingId);
        acceptedBooking.setStatus(BookingStatus.ACCEPTED);
        acceptedBooking.setPassenger(new User());

        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(liveRideStateStore.get(rideId)).thenReturn(Optional.empty());
        when(gpsFixValidator.validate(any(), any(), any())).thenReturn(GpsValidationOutcome.ACCEPTED);
        when(bookingRepository.findBoardingCandidatesForRide(rideId)).thenReturn(List.of(checkedInBooking, acceptedBooking));

        PassengerEtaView checkedInView = new PassengerEtaView(checkedInBookingId, StopType.DROPOFF, Instant.now(), ArrivalState.EN_ROUTE);
        PassengerEtaView acceptedView = new PassengerEtaView(acceptedBookingId, StopType.PICKUP, Instant.now(), ArrivalState.EN_ROUTE);
        when(etaEngine.etaForBooking(any(), any(), eq(checkedInBooking), any())).thenReturn(Optional.of(checkedInView));
        when(etaEngine.etaForBooking(any(), any(), eq(acceptedBooking), any())).thenReturn(Optional.of(acceptedView));

        service.updateLocation(driverId, rideId, request(12.9716, 77.604, Instant.now()));

        verify(liveTrackingBroadcaster).pushToUser(eq(checkedInPassengerId), any(PassengerEtaView.class));
        verify(liveTrackingBroadcaster, never()).pushToUser(eq(acceptedBooking.getPassenger().getId()), any());
    }

    @Test
    void updateLocation_ignoredDuplicate_doesNotBroadcast() {
        LiveRideState lastKnown = new LiveRideState(rideId, 12.9716, 77.602, Instant.now(), new java.util.HashMap<>());
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(liveRideStateStore.get(rideId)).thenReturn(Optional.of(lastKnown));
        when(gpsFixValidator.validate(any(), any(), any())).thenReturn(GpsValidationOutcome.IGNORED_DUPLICATE);

        service.updateLocation(driverId, rideId, request(12.9716, 77.602, Instant.now()));

        verify(liveTrackingBroadcaster, never()).pushToUser(any(), any());
    }

    @Test
    void updateLocation_rejectedOutcome_throwsUnprocessableEntity() {
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(liveRideStateStore.get(rideId)).thenReturn(Optional.empty());
        when(gpsFixValidator.validate(any(), any(), any())).thenReturn(GpsValidationOutcome.REJECTED_IMPOSSIBLE_JUMP);

        assertThatThrownBy(() -> service.updateLocation(driverId, rideId, request(12.9716, 77.604, Instant.now())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("422")
                .hasMessageContaining("REJECTED_IMPOSSIBLE_JUMP");

        verify(liveRideStateStore, never()).save(any(), any());
    }

    @Test
    void getSnapshot_callerIsTheDriver_isAllowed() {
        LiveRideState state = new LiveRideState(rideId, 12.9716, 77.602, Instant.now(), new java.util.HashMap<>());
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(liveRideStateStore.get(rideId)).thenReturn(Optional.of(state));

        RideProgressSnapshot snapshot = service.getSnapshot(driverId, rideId);

        assertThat(snapshot.rideId()).isEqualTo(rideId);
    }

    @Test
    void getSnapshot_callerIsACheckedInPassenger_isAllowed() {
        UUID passengerId = UUID.randomUUID();
        LiveRideState state = new LiveRideState(rideId, 12.9716, 77.602, Instant.now(), new java.util.HashMap<>());
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(bookingRepository.existsByRide_RideIdAndPassenger_IdAndStatus(rideId, passengerId, BookingStatus.CHECKED_IN))
                .thenReturn(true);
        when(liveRideStateStore.get(rideId)).thenReturn(Optional.of(state));

        RideProgressSnapshot snapshot = service.getSnapshot(passengerId, rideId);

        assertThat(snapshot.rideId()).isEqualTo(rideId);
    }

    @Test
    void getSnapshot_callerIsAnUnrelatedUser_throwsForbidden() {
        UUID strangerId = UUID.randomUUID();
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(bookingRepository.existsByRide_RideIdAndPassenger_IdAndStatus(rideId, strangerId, BookingStatus.CHECKED_IN))
                .thenReturn(false);

        assertThatThrownBy(() -> service.getSnapshot(strangerId, rideId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void getSnapshot_noFixEverRecorded_throwsNotFound() {
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(liveRideStateStore.get(rideId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSnapshot(driverId, rideId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

}
