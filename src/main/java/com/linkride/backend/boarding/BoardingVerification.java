package com.linkride.backend.boarding;

import com.linkride.backend.booking.Booking;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Boarding-verification state for exactly one {@link Booking} (Phase 4). Deliberately not columns
 * on {@code Booking} itself — OTP/QR verification is transient authentication data, not booking
 * state, and keeping it here lets {@code Booking} stay focused on booking state alone. One row
 * per booking, created lazily on the first boarding action (QR issue or OTP generation), not
 * eagerly when the booking is accepted.
 *
 * <p>{@code checkedInAt != null} is what "verified" means — there's no separate boolean flag to
 * keep in sync with it.</p>
 */
@Data
@Entity
@Table(name = "boarding_verifications")
public class BoardingVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "verification_id", updatable = false, nullable = false)
    private UUID verificationId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", referencedColumnName = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Column(name = "otp_hash")
    private String otpHash;

    @Column(name = "otp_expires_at")
    private OffsetDateTime otpExpiresAt;

    @Column(name = "otp_attempts", nullable = false)
    private Integer otpAttempts = 0;

    @Column(name = "checked_in_at")
    private OffsetDateTime checkedInAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "checked_in_via")
    private CheckedInVia checkedInVia;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
