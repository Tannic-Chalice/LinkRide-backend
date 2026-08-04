package com.linkride.backend.tracking;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Tunables for the live-tracking pipeline — GPS validation, route progress, arrival detection,
 * and stale-state eviction. See backend/docs/phase-7-live-trip-management.md for the section
 * each property backs.
 */
@Data
@Component
@ConfigurationProperties(prefix = "linkride.tracking")
public class TrackingProperties {

    /** §7.1 step 2 — reject a fix timestamped further than this into the future. */
    private int maxFutureSkewSeconds = 30;

    /** §7.1 step 2 — reject a fix timestamped further than this into the past. */
    private int maxFixAgeSeconds = 120;

    /** §7.1 step 4 — implied speed above this rejects the fix as an impossible jump. */
    private double maxPlausibleSpeedMps = 55.0;

    /** §8 step 6 — distance from the route beyond which a fix is flagged off-route. */
    private double offRouteThresholdMeters = 150.0;

    /** §10 — distance-to-target radius that triggers EN_ROUTE -> APPROACHING. */
    private double approachingRadiusMeters = 500.0;

    /** §10 — distance-to-target radius that triggers -> ARRIVED. */
    private double arrivedRadiusMeters = 50.0;

    /** §11.4 — optional floor on ping frequency; 0 disables the guard. */
    private long minPingIntervalMillis = 0;

    /** §14 — idle duration after which StaleLiveStateReaper evicts a ride's LiveRideState. */
    private Duration staleIdleThreshold = Duration.ofHours(4);

    /**
     * §14 — how often StaleLiveStateReaper sweeps for idle rides. Mirrored as the literal
     * default on {@code @Scheduled}'s {@code fixedDelayString} placeholder (annotation values
     * must be compile-time constants, so this field can't be referenced directly there) — keep
     * both in sync if this default ever changes.
     */
    private long reapIntervalMillis = 900_000; // 15 minutes
}
