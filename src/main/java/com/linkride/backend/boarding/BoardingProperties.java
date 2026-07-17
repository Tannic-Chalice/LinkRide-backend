package com.linkride.backend.boarding;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tunable constants for the passenger boarding/check-in flow (Phase 4), following the same
 * {@code @ConfigurationProperties} pattern as {@link com.linkride.backend.discovery.MatchingProperties}.
 */
@Data
@Component
@ConfigurationProperties(prefix = "linkride.boarding")
public class BoardingProperties {

    /**
     * How long a driver-issued QR stays valid before it must be regenerated (Phase 4.2). Short by
     * design — the driver mints one per passenger right at boarding time, not upfront for the
     * whole ride, so there's no need for a long window.
     */
    private int qrTtlMinutes = 5;

    /**
     * How long a driver-generated OTP stays valid (Phase 4.4). Longer than {@link #qrTtlMinutes}
     * — this fallback involves the passenger checking an email and reading a code aloud, more
     * friction than a QR scan.
     */
    private int otpTtlMinutes = 10;

    /**
     * How many incorrect OTP submissions are allowed before the driver must generate a fresh one
     * (Phase 4.4). The real protection against guessing a 6-digit code isn't the hash — a static
     * SHA-256 of a 6-digit space is trivially precomputable — it's this ceiling combined with the
     * short TTL and the fact that only the ride's authenticated driver can ever submit an attempt.
     */
    private int otpMaxAttempts = 5;
}
