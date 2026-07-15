package com.linkride.backend.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    List<Booking> findByRide_RideId(UUID rideId);

    List<Booking> findByRide_RideIdAndStatus(UUID rideId, BookingStatus status);

    List<Booking> findByPassenger_Id(UUID passengerId);

    List<Booking> findByPassenger_IdAndStatus(UUID passengerId, BookingStatus status);

    /**
     * Backs the duplicate-active-request pre-check (Phase 3.2) — the authoritative guard is
     * {@code uq_bookings_active_ride_passenger} (V5 migration); this is the friendlier error path,
     * so "active" here must stay exactly {@code PENDING}/{@code ACCEPTED} to match that index.
     * Deliberately not a {@code ...StatusIn(List<BookingStatus>)} derived query — that would let a
     * call site pass the wrong set of statuses; hardcoding here makes "active" mean one thing.
     */
    @Query("""
            SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END
            FROM Booking b
            WHERE b.ride.rideId = :rideId
              AND b.passenger.id = :passengerId
              AND b.status IN (com.linkride.backend.booking.BookingStatus.PENDING,
                                com.linkride.backend.booking.BookingStatus.ACCEPTED)
            """)
    boolean existsActiveBookingForRideAndPassenger(@Param("rideId") UUID rideId, @Param("passengerId") UUID passengerId);

    /**
     * Driver cancelled the whole ride (RideService.cancelRide, Phase 3.6) — every still-live
     * request or acceptance on it is cancelled too. Seats aren't released here: the ride is dead,
     * so its available_seats no longer matters.
     */
    @Modifying
    @Query("""
            UPDATE Booking b SET b.status = com.linkride.backend.booking.BookingStatus.CANCELLED,
                   b.cancelledAt = CURRENT_TIMESTAMP,
                   b.cancelledBy = com.linkride.backend.booking.CancelInitiator.DRIVER
            WHERE b.ride.rideId = :rideId
              AND b.status IN (com.linkride.backend.booking.BookingStatus.PENDING,
                                com.linkride.backend.booking.BookingStatus.ACCEPTED)
            """)
    int cancelAllActiveBookingsForRide(@Param("rideId") UUID rideId);

    /**
     * Driver explicitly started the ride (RideService.startRide, Phase 3.6) — any request still
     * PENDING at that moment never got a decision in time. Distinct from REJECTED: the driver
     * didn't decline it, the driver's own start() call simply overtook it. Never triggered by
     * departureTime passing — ride status only ever changes via explicit driver action.
     */
    @Modifying
    @Query("""
            UPDATE Booking b SET b.status = com.linkride.backend.booking.BookingStatus.EXPIRED
            WHERE b.ride.rideId = :rideId
              AND b.status = com.linkride.backend.booking.BookingStatus.PENDING
            """)
    int expireAllPendingBookingsForRide(@Param("rideId") UUID rideId);

    /**
     * Driver explicitly completed the ride (RideService.completeRide, Phase 3.6) — every accepted
     * passenger's booking completes with it.
     */
    @Modifying
    @Query("""
            UPDATE Booking b SET b.status = com.linkride.backend.booking.BookingStatus.COMPLETED
            WHERE b.ride.rideId = :rideId
              AND b.status = com.linkride.backend.booking.BookingStatus.ACCEPTED
            """)
    int completeAllAcceptedBookingsForRide(@Param("rideId") UUID rideId);
}
