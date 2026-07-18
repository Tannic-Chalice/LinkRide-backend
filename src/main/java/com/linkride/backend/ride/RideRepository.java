package com.linkride.backend.ride;

import com.linkride.backend.route.RouteGenerationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RideRepository extends JpaRepository<Ride, UUID> {

    /** Devtools only: every ride created by any of these drivers, for {@code reset()} to enumerate before deleting. */
    List<Ride> findByDriver_IdIn(Collection<UUID> driverIds);

    /**
     * Devtools only: bulk cleanup for {@code reset()}. {@code ride_waypoints} has {@code ON DELETE
     * CASCADE} back to {@code rides} at the database level (V2 migration), so it needs no separate
     * delete here. {@code clearAutomatically = true} for the same reason as {@code
     * VehicleRepository#deleteByOwnerIdIn} — this raw-SQL bulk delete must evict Hibernate's
     * persistence context, or stale managed entities loaded earlier in {@code reset()} still
     * reference a {@code User} about to be deleted, and a later flush throws {@code
     * TransientObjectException}.
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Ride r WHERE r.driver.id IN :driverIds")
    int deleteByDriver_IdIn(@Param("driverIds") Collection<UUID> driverIds);

    /**
     * Coarse, inexpensive candidate pre-filter for passenger search (design doc §2.4) — seat
     * capacity, successful routing, an active status, a departure-time window, and excluding the
     * searching passenger's own rides. Every predicate is a plain indexed-column check; no
     * geometry is involved here (that starts in Phase 2B.4).
     */
    @Query("""
            SELECT r FROM Ride r
            WHERE r.availableSeats >= :passengerCount
              AND r.routeGenerationState = :routeGenerationState
              AND r.status = :status
              AND r.departureTime BETWEEN :windowStart AND :windowEnd
              AND r.driver.id <> :excludedDriverId
            """)
    List<Ride> findCandidatesForSearch(
            @Param("passengerCount") int passengerCount,
            @Param("routeGenerationState") RouteGenerationState routeGenerationState,
            @Param("status") RideStatus status,
            @Param("windowStart") OffsetDateTime windowStart,
            @Param("windowEnd") OffsetDateTime windowEnd,
            @Param("excludedDriverId") UUID excludedDriverId);

    /**
     * Atomic, race-free seat reservation (Phase 3.4 design doc §5) — the WHERE clause's seat and
     * status checks are evaluated under the same row lock Postgres takes for the UPDATE itself, so
     * two concurrent accepts racing for the last seat cannot both succeed. Guards only on
     * {@code status}, deliberately not on {@code departureTime}: ride status changes only via
     * explicit driver action ({@link RideService#startRide}), never inferred from time, so a
     * booking on a SCHEDULED ride whose departure time has quietly passed is still acceptable
     * until the driver actually calls start().
     *
     * @return rows updated — {@code 1} if reserved, {@code 0} if the guard failed (not enough
     * seats, or the ride is no longer SCHEDULED); callers map {@code 0} to a 409 conflict.
     */
    @Modifying
    @Query("""
            UPDATE Ride r SET r.availableSeats = r.availableSeats - :seats, r.updatedAt = CURRENT_TIMESTAMP
            WHERE r.rideId = :rideId
              AND r.availableSeats >= :seats
              AND r.status = com.linkride.backend.ride.RideStatus.SCHEDULED
            """)
    int reserveSeats(@Param("rideId") UUID rideId, @Param("seats") int seats);

    /**
     * Releases seats previously reserved by {@link #reserveSeats}, e.g. on booking cancellation
     * (Phase 3.5). Capped at {@code totalSeats} defensively — should be unreachable in practice
     * since every release corresponds to an earlier successful reservation.
     */
    @Modifying
    @Query("""
            UPDATE Ride r SET r.availableSeats = r.availableSeats + :seats, r.updatedAt = CURRENT_TIMESTAMP
            WHERE r.rideId = :rideId
              AND r.availableSeats + :seats <= r.totalSeats
            """)
    int releaseSeats(@Param("rideId") UUID rideId, @Param("seats") int seats);

    /**
     * Atomic ride-lifecycle transitions (Phase 3.6) — same reasoning as {@link #reserveSeats}:
     * {@code Ride} carries no {@code @Version}, so a plain read-check-then-save in the service
     * layer would leave a real TOCTOU gap (e.g. a driver double-clicking cancel and start in two
     * tabs — both read SCHEDULED, both pass the check, whichever save() lands second silently
     * wins with no error to the loser). Folding the state guard into the UPDATE's WHERE clause
     * closes that the same way seat reservation does: the check and the write happen under one
     * row lock, so at most one of two racing calls can ever succeed.
     *
     * @return rows updated — {@code 1} if transitioned, {@code 0} if the ride wasn't in the
     * required starting state; callers map {@code 0} to a 409 conflict.
     */
    @Modifying
    @Query("""
            UPDATE Ride r SET r.status = com.linkride.backend.ride.RideStatus.CANCELLED, r.updatedAt = CURRENT_TIMESTAMP
            WHERE r.rideId = :rideId
              AND r.status = com.linkride.backend.ride.RideStatus.SCHEDULED
            """)
    int cancelIfScheduled(@Param("rideId") UUID rideId);

    @Modifying
    @Query("""
            UPDATE Ride r SET r.status = com.linkride.backend.ride.RideStatus.IN_PROGRESS, r.updatedAt = CURRENT_TIMESTAMP
            WHERE r.rideId = :rideId
              AND r.status = com.linkride.backend.ride.RideStatus.SCHEDULED
            """)
    int startIfScheduled(@Param("rideId") UUID rideId);

    @Modifying
    @Query("""
            UPDATE Ride r SET r.status = com.linkride.backend.ride.RideStatus.COMPLETED, r.updatedAt = CURRENT_TIMESTAMP
            WHERE r.rideId = :rideId
              AND r.status = com.linkride.backend.ride.RideStatus.IN_PROGRESS
            """)
    int completeIfInProgress(@Param("rideId") UUID rideId);
}
