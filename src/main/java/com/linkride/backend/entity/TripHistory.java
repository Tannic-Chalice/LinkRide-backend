package com.linkride.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "trip_history")
public class TripHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "trip_id", updatable = false, nullable = false)
    private UUID tripId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", referencedColumnName = "id", nullable = false)
    private User driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rider_id", referencedColumnName = "id", nullable = false)
    private User rider;

    @Column(name = "final_cost", nullable = false)
    private BigDecimal finalCost;

    @Column(name = "pickup_address", columnDefinition = "TEXT")
    private String pickupAddress;

    @Column(name = "destination_address", columnDefinition = "TEXT")
    private String destinationAddress;

    @CreationTimestamp
    @Column(name = "completed_at", updatable = false)
    private OffsetDateTime completedAt;
}