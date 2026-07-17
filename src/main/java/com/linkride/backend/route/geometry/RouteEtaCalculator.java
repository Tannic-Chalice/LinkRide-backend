package com.linkride.backend.route.geometry;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Estimated arrival time at an arbitrary point of progress along a route, interpolated
 * proportionally from the route's total distance/duration — the same proportionality assumption
 * {@link CumulativeMetricsCalculator} already makes when annotating each decoded vertex at
 * ride-creation time, applied here to an arbitrary cumulative distance (typically a
 * corridor-derived pickup point, design doc §5.4/§6.4) rather than a decoded vertex. Shared by
 * both hard-constraint validation (Phase 2B.6, the pickup time-window check) and scoring
 * (Phase 2B.7, the time-window-offset component) so the two stages can't drift apart on how ETA
 * is computed.
 *
 * <p>A plain static utility, not a Spring bean — like {@link CumulativeMetricsCalculator} and
 * {@link com.linkride.backend.discovery.corridor.CorridorCalculator}, this is pure math with no
 * external dependency.</p>
 */
public final class RouteEtaCalculator {

    private RouteEtaCalculator() {
    }

    public static OffsetDateTime etaAt(OffsetDateTime departureTime, RouteGeometry geometry, double cumulativeDistanceMeters) {
        double totalDistance = geometry.totalDistanceMeters();
        double proportion = totalDistance == 0 ? 0 : cumulativeDistanceMeters / totalDistance;
        long etaOffsetSeconds = Math.round(proportion * geometry.totalDurationSeconds());
        return departureTime.plusSeconds(etaOffsetSeconds);
    }

    /**
     * Absolute minutes between a desired departure time and the driver's ETA at a point of
     * progress along their route — the shared time-window-offset formula used by both hard-
     * constraint validation (the pickup time-window check) and scoring (the time-window-offset
     * score component), kept here so the two stages can't drift apart on how it's computed.
     */
    public static long minutesOffsetFromEta(
            OffsetDateTime desiredTime, OffsetDateTime departureTime, RouteGeometry geometry, double cumulativeDistanceMeters) {
        OffsetDateTime etaAtPoint = etaAt(departureTime, geometry, cumulativeDistanceMeters);
        return Math.abs(Duration.between(desiredTime, etaAtPoint).toMinutes());
    }
}
