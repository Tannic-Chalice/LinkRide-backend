package com.linkride.backend.boarding;

import com.linkride.backend.booking.Booking;
import com.linkride.backend.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A short-lived, single-use QR credential scoped to exactly one {@link Booking} — not a ride
 * (Phase 4). The driver explicitly selects a passenger from the boarding screen and a token is
 * issued for that booking alone; {@code booking} alone determines the ride, passenger, and
 * driver, so none of those are duplicated here.
 *
 * <p>"Active" (reusable, not yet needing regeneration) means {@code revokedAt == null &&
 * consumedAt == null} — enforced as at most one such row per booking by
 * {@code uq_boarding_tokens_active_per_booking} (V6 migration), not just by service-layer
 * find-or-create logic.</p>
 */
@Data
@Entity
@Table(name = "boarding_tokens")
public class BoardingToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "token_id", updatable = false, nullable = false)
    private UUID tokenId;

    @Column(name = "token", nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", referencedColumnName = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "id", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "issued_at", updatable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;
}
