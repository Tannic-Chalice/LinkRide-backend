package com.linkride.backend.devtools;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tunable constants for the demo seeder/scenario tools (developer-tool only — see {@code
 * devtools} package javadoc), following the same {@code @ConfigurationProperties} pattern as
 * {@link com.linkride.backend.discovery.MatchingProperties}. Every field here only matters when
 * {@link #enabled} is {@code true}; the whole {@code devtools} controller is absent from the
 * Spring context otherwise.
 */
@Data
@Component
@ConfigurationProperties(prefix = "linkride.devtools")
public class DevToolsProperties {

    /** Master switch. Off by default — must be explicitly turned on for local/demo use. */
    private boolean enabled = false;

    /** Email suffix every seeded user gets, e.g. {@code driver03@linkride.seed}. Also the marker {@code reset()} uses to find seeded rows. */
    private String seedEmailDomain = "linkride.seed";

    private int driverCount = 50;
    private int passengerCount = 150;

    /** Rides created per driver (a driver may offer more than one departure). */
    private int minRidesPerDriver = 1;
    private int maxRidesPerDriver = 3;

    /** Spread of ride departure times, starting from "now" at seed time. */
    private int departureSpreadHours = 72;

    /** Fraction of PENDING bookings a driver accepts vs. rejects. */
    private double acceptRate = 0.65;

    /** Of accepted bookings, fraction that get boarded via QR vs. OTP vs. left as a no-show. */
    private double qrCheckInRate = 0.55;
    private double otpCheckInRate = 0.25;
    // remainder becomes NO_SHOW once the ride starts

    /** Fraction of eligible rides driven all the way through start -> complete. */
    private double rideCompletionRate = 0.5;

    /** Fraction of still-PENDING/ACCEPTED bookings cancelled outright (by passenger or driver) before that. */
    private double cancellationRate = 0.08;
}
