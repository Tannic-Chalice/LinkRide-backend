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

    /**
     * How far (in minutes, each direction) the driver's estimated arrival at the derived pickup
     * point may be from the passenger's desired departure time for a corridor match to survive
     * hard-constraint validation (design doc §5.4 "Time compatibility", Phase 2B.6). Deliberately
     * tighter than {@link #departureWindowMinutes}, which only bounds the ride's overall
     * departure time before any pickup point is known — this checks the driver's ETA at the
     * specific point along the route the passenger would actually board.
     */
    private int pickupTimeToleranceMinutes = 30;

    /**
     * Fixed per-stop time overhead (seconds) for a single pickup or drop-off — the "+90 seconds
     * for the actual stop" from design doc §6.1. {@code driverDetourTimeSeconds} in the scoring
     * breakdown (Phase 2B.7) is twice this (one pickup stop, one drop stop): under this project's
     * corridor-derived pickup/drop (§5.2), pickup and drop always land exactly on the driver's own
     * route, so the *distance*-based portion of the §6.1 detour formula is provably zero — the
     * driver never leaves their route to reach a corridor-derived point. This stop-time constant
     * is what's actually left to represent as "driver detour" until real off-route detour
     * computation is added.
     */
    private int stopTimeSeconds = 90;

    /**
     * Hard ceiling on {@code passengerDetourRatio} (design doc §6.2a) — how much extra ground,
     * relative to their own direct origin→destination distance, the passenger covers reaching the
     * derived pickup and walking from the derived drop. Above this, a candidate is routing the
     * passenger backward relative to their own trip, which the doc treats as a correctness bug
     * rather than a taste preference — rejected outright in scoring (Phase 2B.7), not merely
     * down-ranked.
     */
    private double maxPassengerDetourRatio = 1.4;

    /**
     * Composite-score weights (design doc §6.5, {@code w1..w5}) — must be non-negative; there's no
     * requirement that they sum to 1.0 (each component is already normalized to [0,1] before
     * weighting), but keeping them summed to 1.0 makes the composite score itself read as a
     * roughly [0,1] "badness" figure. Ordered here to match design doc §8's stated priority:
     * driver detour weighted highest (driver supply is the scarcer resource), then the two
     * passenger-convenience components (walk fraction, time window), then passenger detour ratio
     * and benefit as secondary signals. Starting points only — see §6.5's own note on eventually
     * replacing these with coefficients learned from match acceptance/completion data.
     */
    private double weightDriverDetourTime = 0.30;
    private double weightPassengerDetourRatio = 0.15;
    private double weightWalkFraction = 0.20;
    private double weightBenefit = 0.15;
    private double weightTimeWindow = 0.20;

    /**
     * Maximum number of ranked matches returned to the client (design doc §7, "top-K, e.g.
     * 10–20") — truncation happens after sorting, so this never changes which candidate ranks
     * best, only how many of the tail get dropped.
     */
    private int maxRankedMatches = 15;
}
