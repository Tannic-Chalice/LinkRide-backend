package com.linkride.backend.boarding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
