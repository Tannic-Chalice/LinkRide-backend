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
public interface BoardingTokenRepository extends JpaRepository<BoardingToken, UUID> {

    Optional<BoardingToken> findByToken(String token);

    /**
     * The single active token for a booking, if any — see {@link BoardingToken}'s javadoc for
     * what "active" means and how it's enforced at the database level. Used to decide whether to
     * reuse an existing QR or mint a new one.
     */
    Optional<BoardingToken> findByBooking_BookingIdAndRevokedAtIsNullAndConsumedAtIsNull(UUID bookingId);

    /**
     * Devtools only: bulk cleanup for {@code reset()} — must run before the bookings themselves are
     * deleted. {@code clearAutomatically = true}: see {@code VehicleRepository#deleteByOwnerIdIn}.
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM BoardingToken t WHERE t.booking.bookingId IN :bookingIds")
    int deleteByBooking_BookingIdIn(@Param("bookingIds") Collection<UUID> bookingIds);
}
