package com.linkride.backend.booking;

import com.linkride.backend.entity.User;
import com.linkride.backend.ride.Ride;
import jakarta.persistence.Column;
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
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A passenger's request to join a ride (Phase 3). One-directional relations only, matching the
 * rest of the codebase (see architecture.md) — {@code Ride}/{@code User} never hold a back-reference
 * collection to their bookings; fetch via {@link BookingRepository} instead.
 *
 * <p>Pickup/drop are plain lat/lng, not the {@code GeoPoint}/PostGIS embeddable {@code Ride} uses —
 * nothing ever spatially queries a booking's pickup/drop, and a corridor-derived point (Phase 2B.5)
 * has no name/address the way {@code GeoPoint} requires.</p>
 */
@Data
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "booking_id", updatable = false, nullable = false)
    private UUID bookingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_id", referencedColumnName = "ride_id", nullable = false)
    private Ride ride;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id", referencedColumnName = "id", nullable = false)
    private User passenger;

    @Column(name = "seats_requested", nullable = false)
    private Integer seatsRequested = 1;

    @Column(name = "pickup_lat", nullable = false)
    private Double pickupLat;

    @Column(name = "pickup_lng", nullable = false)
    private Double pickupLng;

    @Column(name = "pickup_label")
    private String pickupLabel;

    @Column(name = "drop_lat", nullable = false)
    private Double dropLat;

    @Column(name = "drop_lng", nullable = false)
    private Double dropLng;

    @Column(name = "drop_label")
    private String dropLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by")
    private CancelInitiator cancelledBy;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
