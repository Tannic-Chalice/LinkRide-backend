package com.linkride.backend.booking;

import com.linkride.backend.entity.User;
import com.linkride.backend.repository.UserRepository;
import com.linkride.backend.ride.Ride;
import com.linkride.backend.ride.RideRepository;
import com.linkride.backend.ride.RideStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private static final String DUPLICATE_ACTIVE_BOOKING_MESSAGE =
            "You already have an active request for this ride";

    private final BookingRepository bookingRepository;
    private final RideRepository rideRepository;
    private final UserRepository userRepository;

    /**
     * Creates a PENDING booking request. Business rules enforced:
     * <ul>
     *   <li>Passenger and ride must exist.</li>
     *   <li>A passenger may not request their own ride.</li>
     *   <li>The ride must be {@code SCHEDULED} — no requests once it's started/finished/cancelled.</li>
     *   <li>Enough seats must be available — a soft check; the actual reservation happens
     *       atomically at accept time (Phase 3.4), not here.</li>
     *   <li>At most one active ({@code PENDING}/{@code ACCEPTED}) request per (ride, passenger) —
     *       checked here for a clean error message, enforced authoritatively by
     *       {@code uq_bookings_active_ride_passenger} against the race this pre-check can't close.</li>
     * </ul>
     */
    @Override
    @Transactional
    public BookingResponse requestBooking(UUID passengerId, UUID rideId, BookingCreateRequest request) {
        User passenger = userRepository.findById(passengerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Authenticated user not found in the system"));

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));

        if (ride.getDriver().getId().equals(passengerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot request to join your own ride");
        }

        if (ride.getStatus() != RideStatus.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This ride is not open for booking");
        }

        int seatsRequested = request.getSeatsRequested() != null ? request.getSeatsRequested() : 1;

        if (ride.getAvailableSeats() < seatsRequested) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough seats available on this ride");
        }

        if (bookingRepository.existsActiveBookingForRideAndPassenger(rideId, passengerId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATE_ACTIVE_BOOKING_MESSAGE);
        }

        Booking booking = new Booking();
        booking.setRide(ride);
        booking.setPassenger(passenger);
        booking.setSeatsRequested(seatsRequested);
        booking.setPickupLat(request.getPickup().getLatitude());
        booking.setPickupLng(request.getPickup().getLongitude());
        booking.setPickupLabel(request.getPickupLabel());
        booking.setDropLat(request.getDrop().getLatitude());
        booking.setDropLng(request.getDrop().getLongitude());
        booking.setDropLabel(request.getDropLabel());

        Booking saved;
        try {
            // saveAndFlush, not save: forces the INSERT (and the unique-index check) to run now,
            // inside this try block, instead of at transaction commit where it can no longer be
            // caught here — that's what closes the pre-check's TOCTOU gap.
            saved = bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATE_ACTIVE_BOOKING_MESSAGE);
        }

        return BookingResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> listBookingsForRide(UUID driverId, UUID rideId, BookingStatus statusFilter) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));

        if (!ride.getDriver().getId().equals(driverId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not drive this ride");
        }

        List<Booking> bookings = statusFilter != null
                ? bookingRepository.findByRide_RideIdAndStatus(rideId, statusFilter)
                : bookingRepository.findByRide_RideId(rideId);

        return bookings.stream().map(BookingResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> listMyBookings(UUID passengerId, BookingStatus statusFilter) {
        List<Booking> bookings = statusFilter != null
                ? bookingRepository.findByPassenger_IdAndStatus(passengerId, statusFilter)
                : bookingRepository.findByPassenger_Id(passengerId);

        return bookings.stream().map(BookingResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBooking(UUID requesterId, UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        boolean isPassenger = booking.getPassenger().getId().equals(requesterId);
        boolean isDriver = booking.getRide().getDriver().getId().equals(requesterId);

        if (!isPassenger && !isDriver) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot view this booking");
        }

        return BookingResponse.from(booking);
    }

    /**
     * Accepts a PENDING booking, atomically reserving the seat(s) it needs. Business rules
     * enforced:
     * <ul>
     *   <li>Only the ride's driver may decide it.</li>
     *   <li>Only a {@code PENDING} booking can be accepted (covers double-accept, accept-after-
     *       reject, etc. on the sequential path).</li>
     *   <li>The ride must still be {@code SCHEDULED} — a friendly pre-check here, but the
     *       authoritative guard is {@link RideRepository#reserveSeats}'s own WHERE clause, which
     *       re-checks both status and seat count atomically under the ride row's lock.</li>
     *   <li>If two accepts race for the last seat, the loser gets a clean 409 from
     *       {@code reserveSeats} returning 0 rows — no partial state, since the whole method is
     *       one transaction.</li>
     *   <li>If the same booking is double-accepted concurrently, {@code @Version} on
     *       {@link Booking} makes the loser's {@code saveAndFlush} throw an optimistic-lock
     *       exception — and because the seat reservation happened in the same transaction, that
     *       failure rolls back the loser's spurious reservation too, not just the status write.</li>
     * </ul>
     */
    @Override
    @Transactional
    public BookingResponse acceptBooking(UUID driverId, UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        Ride ride = booking.getRide();

        if (!ride.getDriver().getId().equals(driverId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not drive this ride");
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking is not pending a decision");
        }

        if (ride.getStatus() != RideStatus.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This ride is no longer open for booking");
        }

        int rowsReserved = rideRepository.reserveSeats(ride.getRideId(), booking.getSeatsRequested());
        if (rowsReserved == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Not enough seats available, or this ride is no longer open for booking");
        }

        booking.setStatus(BookingStatus.ACCEPTED);
        booking.setDecidedAt(OffsetDateTime.now());

        try {
            // saveAndFlush so the @Version check runs now, inside this try block — same reasoning
            // as requestBooking's saveAndFlush. A caught failure here still rolls back the whole
            // transaction (including the reserveSeats update above), so no seat is ever leaked.
            bookingRepository.saveAndFlush(booking);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This booking was already updated by another request");
        }

        return BookingResponse.from(booking);
    }

    /**
     * Declines a PENDING booking. No seats are involved — none were ever reserved for a request
     * that was never accepted.
     */
    @Override
    @Transactional
    public BookingResponse rejectBooking(UUID driverId, UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (!booking.getRide().getDriver().getId().equals(driverId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not drive this ride");
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking is not pending a decision");
        }

        booking.setStatus(BookingStatus.REJECTED);
        booking.setDecidedAt(OffsetDateTime.now());

        try {
            bookingRepository.saveAndFlush(booking);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This booking was already updated by another request");
        }

        return BookingResponse.from(booking);
    }

    /**
     * Either party — the booking's passenger or the ride's driver — may cancel it, while the ride
     * is still {@code SCHEDULED}. Business rules enforced:
     * <ul>
     *   <li>Only the booking's own passenger or the ride's driver may cancel it.</li>
     *   <li>Only {@code PENDING} or {@code ACCEPTED} bookings can be cancelled — the rest are
     *       already terminal.</li>
     *   <li>Can't cancel once the ride has left {@code SCHEDULED} (driver started/cancelled/
     *       completed it) — same "explicit driver action, not time-based" rule as everywhere else
     *       in Phase 3.</li>
     *   <li>If the booking was {@code ACCEPTED}, its seat is released atomically, in the same
     *       transaction as the status write — the same self-healing race protection as
     *       {@link #acceptBooking}: two concurrent cancels on the same booking can't both succeed,
     *       because the loser's optimistic-lock failure rolls back its seat release too.</li>
     * </ul>
     */
    @Override
    @Transactional
    public BookingResponse cancelBooking(UUID requesterId, UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        Ride ride = booking.getRide();
        boolean isPassenger = booking.getPassenger().getId().equals(requesterId);
        boolean isDriver = ride.getDriver().getId().equals(requesterId);

        if (!isPassenger && !isDriver) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot cancel this booking");
        }

        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking can no longer be cancelled");
        }

        if (ride.getStatus() != RideStatus.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This ride has already started, finished, or been cancelled");
        }

        if (booking.getStatus() == BookingStatus.ACCEPTED) {
            rideRepository.releaseSeats(ride.getRideId(), booking.getSeatsRequested());
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(OffsetDateTime.now());
        booking.setCancelledBy(isPassenger ? CancelInitiator.PASSENGER : CancelInitiator.DRIVER);

        try {
            bookingRepository.saveAndFlush(booking);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This booking was already updated by another request");
        }

        return BookingResponse.from(booking);
    }
}
