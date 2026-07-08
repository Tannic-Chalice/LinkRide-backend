package com.linkride.backend.ride;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RideWaypointRepository extends JpaRepository<RideWaypoint, UUID> {
    List<RideWaypoint> findByRide_RideIdOrderBySequenceAsc(UUID rideId);
}
