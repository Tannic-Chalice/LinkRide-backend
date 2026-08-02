package com.linkride.backend.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row per (user, physical device/install) — a user may hold several active tokens at once
 * (Phase 6 — see backend/docs/phase-6-notifications.md §5.2/§9). Registration upserts on {@code
 * fcmToken}, not {@code (userId, platform)}: FCM itself rotates tokens independently of any
 * LinkRide action, and a token belongs to exactly one row at a time.
 *
 * <p>{@code active} is soft-deactivated on an FCM {@code UNREGISTERED} response or explicit
 * logout — never hard-deleted (ADR-5); no {@code @Version} — races here (two near-simultaneous
 * registrations of the same token) are harmless last-write-wins on {@code lastSeenAt}/{@code
 * active}, not a correctness concern worth optimistic locking.</p>
 */
@Data
@Entity
@Table(name = "device_tokens")
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "device_token_id", updatable = false, nullable = false)
    private UUID deviceTokenId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "fcm_token", nullable = false, unique = true)
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false)
    private DevicePlatform platform;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt = OffsetDateTime.now();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
