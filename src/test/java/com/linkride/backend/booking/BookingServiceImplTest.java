package com.linkride.backend.booking;

import com.linkride.backend.discovery.LatLngDto;
import com.linkride.backend.entity.User;
import com.linkride.backend.location.GeoPoint;
import com.linkride.backend.repository.UserRepository;
import com.linkride.backend.ride.Ride;
import com.linkride.backend.ride.RideRepository;
import com.linkride.backend.ride.RideStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private RideRepository rideRepository;
    @Mock
    private UserRepository userRepository;

    private BookingServiceImpl bookingService;

    private UUID driverId;
    private UUID passengerId;
    private UUID rideId;
    private User driver;
    private User passenger;
    private Ride ride;

    @BeforeEach
    void setUp() {
        bookingService = new BookingServiceImpl(bookingRepository, rideRepository, userRepository);

        driverId = UUID.randomUUID();
        passengerId = UUID.randomUUID();
        rideId = UUID.randomUUID();

        driver = new User();
        driver.setId(driverId);

        passenger = new User();
        passenger.setId(passengerId);

        ride = new Ride();
        ride.setRideId(rideId);
        ride.setDriver(driver);
        ride.setStatus(RideStatus.SCHEDULED);
        ride.setAvailableSeats(3);
        ride.setTotalSeats(4);
        ride.setPickup(new GeoPoint("Test Pickup", "123 Test St", null));
        ride.setDestination(new GeoPoint("Test Destination", "456 Test Ave", null));

        lenient().when(userRepository.findById(passengerId)).thenReturn(Optional.of(passenger));
        // Own-ride test calls requestBooking(driverId, ...) — the driver acting as the caller.
        lenient().when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        lenient().when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        lenient().when(bookingRepository.existsActiveBookingForRideAndPassenger(rideId, passengerId))
                .thenReturn(false);
        // A new Booking() already defaults status to PENDING (see Booking.java), so this doesn't
        // need to force it — it mirrors a real save/flush round trip: whatever the caller set on
        // the entity is what comes back, just with an id assigned if it didn't have one yet.
        lenient().when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getBookingId() == null) {
                b.setBookingId(UUID.randomUUID());
            }
            return b;
        });
        lenient().when(rideRepository.reserveSeats(eq(rideId), anyInt())).thenReturn(1);
    }

    private BookingCreateRequest validRequest() {
        BookingCreateRequest request = new BookingCreateRequest();
        request.setSeatsRequested(2);
        request.setPickup(new LatLngDto(12.97, 77.59));
        request.setDrop(new LatLngDto(13.02, 77.63));
        return request;
    }

    private Booking pendingBooking() {
        Booking booking = new Booking();
        booking.setBookingId(UUID.randomUUID());
        booking.setRide(ride);
        booking.setPassenger(passenger);
        booking.setSeatsRequested(1);
        booking.setPickupLat(12.97);
        booking.setPickupLng(77.59);
        booking.setDropLat(13.02);
        booking.setDropLng(77.63);
        booking.setStatus(BookingStatus.PENDING);
        return booking;
    }

    @Test
    void requestBooking_happyPath_createsPendingBookingWithRequestedSeats() {
        BookingResponse response = bookingService.requestBooking(passengerId, rideId, validRequest());

        assertThat(response.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.getSeatsRequested()).isEqualTo(2);
        assertThat(response.getRideId()).isEqualTo(rideId);
        assertThat(response.getPassengerId()).isEqualTo(passengerId);
    }

    @Test
    void requestBooking_seatsRequestedOmitted_defaultsToOne() {
        BookingCreateRequest request = validRequest();
        request.setSeatsRequested(null);

        bookingService.requestBooking(passengerId, rideId, request);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSeatsRequested()).isEqualTo(1);
    }

    @Test
    void requestBooking_passengerNotFound_throws404() {
        when(userRepository.findById(passengerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.requestBooking(passengerId, rideId, validRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void requestBooking_rideNotFound_throws404() {
        when(rideRepository.findById(rideId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.requestBooking(passengerId, rideId, validRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ride not found");
    }

    @Test
    void requestBooking_ownRide_throws403() {
        assertThatThrownBy(() -> bookingService.requestBooking(driverId, rideId, validRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("own ride");
    }

    @Test
    void requestBooking_rideNotScheduled_throws409() {
        ride.setStatus(RideStatus.IN_PROGRESS);

        assertThatThrownBy(() -> bookingService.requestBooking(passengerId, rideId, validRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not open for booking");
    }

    @Test
    void requestBooking_notEnoughSeats_throws409() {
        ride.setAvailableSeats(1);

        assertThatThrownBy(() -> bookingService.requestBooking(passengerId, rideId, validRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not enough seats");
    }

    @Test
    void requestBooking_duplicateActiveBooking_throws409() {
        when(bookingRepository.existsActiveBookingForRideAndPassenger(rideId, passengerId))
                .thenReturn(true);

        assertThatThrownBy(() -> bookingService.requestBooking(passengerId, rideId, validRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already have an active request");
    }

    @Test
    void requestBooking_duplicateActiveBookingRace_savesFlushThrowsConstraintViolation_throws409() {
        // The pre-check passed (existsActiveBookingForRideAndPassenger = false) but a concurrent
        // request for the same (ride, passenger) won the race and committed first — the unique
        // partial index catches it at flush time instead.
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenThrow(new DataIntegrityViolationException("uq_bookings_active_ride_passenger"));

        assertThatThrownBy(() -> bookingService.requestBooking(passengerId, rideId, validRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already have an active request");
    }

    @Test
    void listBookingsForRide_asDriver_returnsUnfilteredBookings() {
        when(bookingRepository.findByRide_RideId(rideId)).thenReturn(List.of(pendingBooking()));

        List<BookingResponse> result = bookingService.listBookingsForRide(driverId, rideId, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRideId()).isEqualTo(rideId);
    }

    @Test
    void listBookingsForRide_withStatusFilter_usesFilteredQuery() {
        when(bookingRepository.findByRide_RideIdAndStatus(rideId, BookingStatus.PENDING))
                .thenReturn(List.of(pendingBooking()));

        List<BookingResponse> result = bookingService.listBookingsForRide(driverId, rideId, BookingStatus.PENDING);

        assertThat(result).hasSize(1);
        verify(bookingRepository).findByRide_RideIdAndStatus(rideId, BookingStatus.PENDING);
    }

    @Test
    void listBookingsForRide_notTheDriver_throws403() {
        assertThatThrownBy(() -> bookingService.listBookingsForRide(passengerId, rideId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("do not drive");
    }

    @Test
    void listBookingsForRide_rideNotFound_throws404() {
        when(rideRepository.findById(rideId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.listBookingsForRide(driverId, rideId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ride not found");
    }

    @Test
    void listMyBookings_returnsPassengersBookings() {
        when(bookingRepository.findByPassenger_Id(passengerId)).thenReturn(List.of(pendingBooking()));

        List<BookingResponse> result = bookingService.listMyBookings(passengerId, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPassengerId()).isEqualTo(passengerId);
    }

    @Test
    void listMyBookings_withStatusFilter_usesFilteredQuery() {
        when(bookingRepository.findByPassenger_IdAndStatus(passengerId, BookingStatus.ACCEPTED))
                .thenReturn(List.of());

        bookingService.listMyBookings(passengerId, BookingStatus.ACCEPTED);

        verify(bookingRepository).findByPassenger_IdAndStatus(passengerId, BookingStatus.ACCEPTED);
    }

    @Test
    void getBooking_asOwningPassenger_succeeds() {
        Booking booking = pendingBooking();
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.getBooking(passengerId, booking.getBookingId());

        assertThat(response.getBookingId()).isEqualTo(booking.getBookingId());
    }

    @Test
    void getBooking_asRideDriver_succeeds() {
        Booking booking = pendingBooking();
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.getBooking(driverId, booking.getBookingId());

        assertThat(response.getBookingId()).isEqualTo(booking.getBookingId());
    }

    @Test
    void getBooking_stranger_throws403() {
        Booking booking = pendingBooking();
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));

        UUID strangerId = UUID.randomUUID();

        assertThatThrownBy(() -> bookingService.getBooking(strangerId, booking.getBookingId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot view");
    }

    @Test
    void getBooking_notFound_throws404() {
        UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getBooking(passengerId, bookingId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Booking not found");
    }

    @Test
    void acceptBooking_happyPath_reservesSeatsAndMarksAccepted() {
        Booking booking = pendingBooking();
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.acceptBooking(driverId, booking.getBookingId());

        assertThat(response.getStatus()).isEqualTo(BookingStatus.ACCEPTED);
        assertThat(booking.getDecidedAt()).isNotNull();
        verify(rideRepository).reserveSeats(rideId, booking.getSeatsRequested());
    }

    @Test
    void acceptBooking_notFound_throws404() {
        UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.acceptBooking(driverId, bookingId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Booking not found");
    }

    @Test
    void acceptBooking_notTheDriver_throws403() {
        Booking booking = pendingBooking();
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.acceptBooking(passengerId, booking.getBookingId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("do not drive");

        verify(rideRepository, never()).reserveSeats(any(UUID.class), anyInt());
    }

    @Test
    void acceptBooking_alreadyDecided_throws409() {
        Booking booking = pendingBooking();
        booking.setStatus(BookingStatus.ACCEPTED);
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.acceptBooking(driverId, booking.getBookingId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not pending a decision");

        verify(rideRepository, never()).reserveSeats(any(UUID.class), anyInt());
    }

    @Test
    void acceptBooking_rideNoLongerScheduled_throws409PreCheck() {
        Booking booking = pendingBooking();
        ride.setStatus(RideStatus.CANCELLED);
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.acceptBooking(driverId, booking.getBookingId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no longer open for booking");

        // The friendly pre-check caught it before ever attempting the atomic reservation.
        verify(rideRepository, never()).reserveSeats(any(UUID.class), anyInt());
    }

    @Test
    void acceptBooking_lastSeatLostToConcurrentAccept_throws409() {
        Booking booking = pendingBooking();
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));
        // reserveSeats returning 0 is the real signal a race was lost — status/seat pre-checks
        // passed moments earlier but a concurrent accept won the row lock and took the seat first.
        when(rideRepository.reserveSeats(rideId, booking.getSeatsRequested())).thenReturn(0);

        assertThatThrownBy(() -> bookingService.acceptBooking(driverId, booking.getBookingId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not enough seats");

        verify(bookingRepository, never()).saveAndFlush(any(Booking.class));
    }

    @Test
    void acceptBooking_concurrentModificationOfSameBooking_throws409() {
        Booking booking = pendingBooking();
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Booking.class, booking.getBookingId()));

        assertThatThrownBy(() -> bookingService.acceptBooking(driverId, booking.getBookingId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already updated by another request");
    }

    @Test
    void rejectBooking_happyPath_marksRejected() {
        Booking booking = pendingBooking();
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.rejectBooking(driverId, booking.getBookingId());

        assertThat(response.getStatus()).isEqualTo(BookingStatus.REJECTED);
        assertThat(booking.getDecidedAt()).isNotNull();
        verify(rideRepository, never()).reserveSeats(any(UUID.class), anyInt());
    }

    @Test
    void rejectBooking_notTheDriver_throws403() {
        Booking booking = pendingBooking();
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.rejectBooking(passengerId, booking.getBookingId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("do not drive");
    }

    @Test
    void rejectBooking_alreadyDecided_throws409() {
        Booking booking = pendingBooking();
        booking.setStatus(BookingStatus.REJECTED);
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.rejectBooking(driverId, booking.getBookingId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not pending a decision");
    }

    @Test
    void rejectBooking_concurrentModification_throws409() {
        Booking booking = pendingBooking();
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Booking.class, booking.getBookingId()));

        assertThatThrownBy(() -> bookingService.rejectBooking(driverId, booking.getBookingId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already updated by another request");
    }

    @Test
    void cancelBooking_passengerCancelsPending_noSeatRelease() {
        Booking booking = pendingBooking();
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.cancelBooking(passengerId, booking.getBookingId());

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancelledBy()).isEqualTo(CancelInitiator.PASSENGER);
        assertThat(booking.getCancelledAt()).isNotNull();
        verify(rideRepository, never()).releaseSeats(any(UUID.class), anyInt());
    }

    @Test
    void cancelBooking_passengerCancelsAccepted_releasesSeat() {
        Booking booking = pendingBooking();
        booking.setStatus(BookingStatus.ACCEPTED);
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));

        bookingService.cancelBooking(passengerId, booking.getBookingId());

        verify(rideRepository).releaseSeats(rideId, booking.getSeatsRequested());
    }

    @Test
    void cancelBooking_driverCancelsAccepted_releasesSeatAndMarksDriverInitiated() {
        Booking booking = pendingBooking();
        booking.setStatus(BookingStatus.ACCEPTED);
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.cancelBooking(driverId, booking.getBookingId());

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancelledBy()).isEqualTo(CancelInitiator.DRIVER);
        verify(rideRepository).releaseSeats(rideId, booking.getSeatsRequested());
    }

    @Test
    void cancelBooking_stranger_throws403() {
        Booking booking = pendingBooking();
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));
        UUID strangerId = UUID.randomUUID();

        assertThatThrownBy(() -> bookingService.cancelBooking(strangerId, booking.getBookingId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot cancel");
    }

    @Test
    void cancelBooking_notFound_throws404() {
        UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancelBooking(passengerId, bookingId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Booking not found");
    }

    @Test
    void cancelBooking_alreadyTerminal_throws409() {
        Booking booking = pendingBooking();
        booking.setStatus(BookingStatus.REJECTED);
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(passengerId, booking.getBookingId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no longer be cancelled");
    }

    @Test
    void cancelBooking_rideNotScheduled_throws409() {
        Booking booking = pendingBooking();
        booking.setStatus(BookingStatus.ACCEPTED);
        ride.setStatus(RideStatus.IN_PROGRESS);
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(passengerId, booking.getBookingId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already started, finished, or been cancelled");

        verify(rideRepository, never()).releaseSeats(any(UUID.class), anyInt());
    }

    @Test
    void cancelBooking_concurrentModification_throws409() {
        Booking booking = pendingBooking();
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Booking.class, booking.getBookingId()));

        assertThatThrownBy(() -> bookingService.cancelBooking(passengerId, booking.getBookingId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already updated by another request");
    }
}
