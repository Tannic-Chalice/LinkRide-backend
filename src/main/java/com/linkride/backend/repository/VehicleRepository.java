package com.linkride.backend.repository;

import com.linkride.backend.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    List<Vehicle> findByOwnerId(UUID ownerId);

    /**
     * Resolves the vehicle a ride should be created against: the owner's active vehicle,
     * gated on admin verification. Used by {@code RideService} to determine driver eligibility —
     * empty means "not eligible to drive" (no active vehicle, or it isn't verified yet).
     */
    Optional<Vehicle> findByOwnerIdAndIsActiveTrueAndIsVerifiedTrue(UUID ownerId);

    /**
     * Used to enforce unique number plate constraint at the service level
     * before attempting a DB insert, allowing us to return a clean 409 Conflict.
     */
    boolean existsByNumberPlate(String numberPlate);
}