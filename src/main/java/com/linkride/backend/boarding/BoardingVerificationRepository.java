package com.linkride.backend.boarding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BoardingVerificationRepository extends JpaRepository<BoardingVerification, UUID> {

    Optional<BoardingVerification> findByBooking_BookingId(UUID bookingId);

    /**
     * Devtools only: bulk cleanup for {@code reset()} — must run before the bookings themselves are
     * deleted. {@code clearAutomatically = true}: see {@code VehicleRepository#deleteByOwnerIdIn}.
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM BoardingVerification v WHERE v.booking.bookingId IN :bookingIds")
    int deleteByBooking_BookingIdIn(@Param("bookingIds") Collection<UUID> bookingIds);
}
