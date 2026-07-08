package com.linkride.backend.ride;

import com.linkride.backend.location.GeoPoint;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.UUID;

@Data
@Entity
@Table(name = "ride_waypoints")
public class RideWaypoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "waypoint_id", updatable = false, nullable = false)
    private UUID waypointId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_id", referencedColumnName = "ride_id", nullable = false)
    private Ride ride;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Embedded
    private GeoPoint location;
}
