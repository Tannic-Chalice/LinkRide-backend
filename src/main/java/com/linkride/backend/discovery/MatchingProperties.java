package com.linkride.backend.discovery;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tunable constants for the Trip Discovery matching pipeline, following the same
 * {@code @ConfigurationProperties} pattern as {@link com.linkride.backend.route.google.GoogleMapsProperties}.
 */
@Data
@Component
@ConfigurationProperties(prefix = "linkride.matching")
public class MatchingProperties {

    /**
     * How far (in minutes, each direction) a driver's departure time may be from the passenger's
     * desired departure time to be considered a candidate at all — deliberately wide (design doc
     * §2.4), since actual feasibility is decided later by detour-time math, not this filter.
     */
    private int departureWindowMinutes = 90;

    /**
     * Default walking tolerance (meters) used for shared-corridor coverage (design doc §1.4,
     * §5.1) when the passenger's request doesn't override it — both as the per-point "is this
     * covered" distance and as the padding for the mandatory bounding-box pre-check, since the
     * same value is a mathematically valid (and cheap) superset test for the per-point check.
     */
    private int corridorWalkToleranceMeters = 750;

    /**
     * Minimum fraction of the passenger's own route that must be covered by a shared corridor
     * run for it to be a real candidate at all (design doc §5.1) — this is what rejects routes
     * that merely cross the passenger's path instead of genuinely sharing a leg of it.
     */
    private double minOverlapFraction = 0.15;
}
