package com.linkride.backend.repository;

import com.linkride.backend.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    List<Vehicle> findByOwnerId(UUID ownerId);

    /**
     * Used to enforce unique number plate constraint at the service level
     * before attempting a DB insert, allowing us to return a clean 409 Conflict.
     */
    boolean existsByNumberPlate(String numberPlate);
}