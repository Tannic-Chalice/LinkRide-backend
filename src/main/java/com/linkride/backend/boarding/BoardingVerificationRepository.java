package com.linkride.backend.boarding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BoardingVerificationRepository extends JpaRepository<BoardingVerification, UUID> {

    Optional<BoardingVerification> findByBooking_BookingId(UUID bookingId);
}
