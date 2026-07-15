package com.linkride.backend.booking;

import java.util.List;
import java.util.UUID;

public interface BookingService {

    BookingResponse requestBooking(UUID passengerId, UUID rideId, BookingCreateRequest request);

    /** Driver-only: every booking on a ride they drive, optionally filtered by status. */
    List<BookingResponse> listBookingsForRide(UUID driverId, UUID rideId, BookingStatus statusFilter);

    /** A passenger's own bookings across all rides, optionally filtered by status. */
    List<BookingResponse> listMyBookings(UUID passengerId, BookingStatus statusFilter);

    /** Visible to the booking's own passenger or the ride's driver — nobody else. */
    BookingResponse getBooking(UUID requesterId, UUID bookingId);

    /** Driver-only: accepts a PENDING request, atomically reserving the seat(s) it needs. */
    BookingResponse acceptBooking(UUID driverId, UUID bookingId);

    /** Driver-only: declines a PENDING request. No seats involved — none were ever reserved. */
    BookingResponse rejectBooking(UUID driverId, UUID bookingId);

    /**
     * Either the booking's passenger or the ride's driver may cancel it, while the ride is still
     * {@code SCHEDULED}. Releases the reserved seat if the booking was {@code ACCEPTED}; a no-op
     * seat-wise if it was still {@code PENDING}.
     */
    BookingResponse cancelBooking(UUID requesterId, UUID bookingId);
}
