package com.linkride.backend.ride;

import com.linkride.backend.entity.User;
import com.linkride.backend.entity.Vehicle;
import com.linkride.backend.location.GeoPoint;
import com.linkride.backend.route.RouteGenerationState;
import com.linkride.backend.route.geometry.RouteGeometry;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "rides")
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ride_id", updatable = false, nullable = false)
    private UUID rideId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", referencedColumnName = "id", nullable = false)
    private User driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", referencedColumnName = "car_id", nullable = false)
    private Vehicle vehicle;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "name", column = @Column(name = "pickup_name", nullable = false)),
            @AttributeOverride(name = "address", column = @Column(name = "pickup_address", columnDefinition = "TEXT", nullable = false)),
            @AttributeOverride(name = "point", column = @Column(name = "pickup_point", columnDefinition = "geography(Point,4326)", nullable = false))
    })
    private GeoPoint pickup;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "name", column = @Column(name = "destination_name", nullable = false)),
            @AttributeOverride(name = "address", column = @Column(name = "destination_address", columnDefinition = "TEXT", nullable = false)),
            @AttributeOverride(name = "point", column = @Column(name = "destination_point", columnDefinition = "geography(Point,4326)", nullable = false))
    })
    private GeoPoint destination;

    @Column(name = "departure_time", nullable = false)
    private OffsetDateTime departureTime;

    @Column(name = "offered_seats", nullable = false)
    private Integer offeredSeats;

    @Column(name = "total_seats", nullable = false)
    private Integer totalSeats;

    @Column(name = "available_seats", nullable = false)
    private Integer availableSeats;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "repeat_schedule", columnDefinition = "jsonb")
    private RepeatSchedule repeatSchedule;

    @Column(name = "quiet_ride")
    private Boolean quietRide;

    @Column(name = "ac_preference")
    private Boolean acPreference;

    @Column(name = "trunk_space")
    private Boolean trunkSpace;

    @Column(name = "route_polyline")
    private String routePolyline;

    @Column(name = "estimated_distance_meters")
    private Integer estimatedDistanceMeters;

    @Column(name = "estimated_duration_seconds")
    private Integer estimatedDurationSeconds;

    /**
     * Decoded, cumulative-distance/time-annotated route geometry (design doc §2.1) — computed
     * once here at creation time so passenger search never has to redecode this ride's polyline.
     * Null unless {@code routeGenerationState == READY}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "route_geometry", columnDefinition = "jsonb")
    private RouteGeometry routeGeometry;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_generation_state", nullable = false)
    private RouteGenerationState routeGenerationState = RouteGenerationState.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RideStatus status = RideStatus.SCHEDULED;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
