package com.linkride.backend.repository;

import com.linkride.backend.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    List<Vehicle> findByOwnerId(UUID ownerId);

    /**
     * Devtools only: bulk cleanup for {@code reset()} — deletes every vehicle owned by a seeded
     * user. {@code clearAutomatically = true} because this raw-SQL bulk delete bypasses Hibernate's
     * persistence context entirely — without it, stale managed {@code Vehicle}/{@code Ride}
     * entities loaded earlier in the same transaction (just to collect their ids) keep referencing
     * a soon-to-be-deleted {@code User}, and a later flush throws {@code TransientObjectException}.
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Vehicle v WHERE v.owner.id IN :ownerIds")
    int deleteByOwnerIdIn(@Param("ownerIds") Collection<UUID> ownerIds);

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