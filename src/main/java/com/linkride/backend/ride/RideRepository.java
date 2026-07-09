package com.linkride.backend.ride;

import com.linkride.backend.route.RouteGenerationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface RideRepository extends JpaRepository<Ride, UUID> {

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
}
