package com.linkride.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "car_id", updatable = false, nullable = false)
    private UUID carId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", referencedColumnName = "id", nullable = false)
    private User owner;

    @Column(name = "number_plate", nullable = false, unique = true)
    private String numberPlate;
    
    @Column(name = "car_make", nullable = false)
    private String carMake;

    @Column(name = "car_model", nullable = false)
    private String carModel;

    @Column(name = "colour")
    private String colour;

    @Column(name = "no_of_seats", nullable = false)
    private Integer noOfSeats;

    @Column(name = "has_ac")
    private Boolean hasAc = true;

    @Column(name = "is_verified")
    private Boolean isVerified = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}