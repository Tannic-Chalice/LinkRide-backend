package com.linkride.backend.tracking;

import java.util.UUID;

/**
 * One driver waypoint's progress along the route (§8 step 4 of
 * backend/docs/phase-7-live-trip-management.md). Recomputed fresh on every
 * {@link RouteProgressEngine#computeProgress} call — never memoized (§6.1).
 */
public record WaypointProgress(
        UUID waypointId,
        int sequence,
        double cumulativeDistanceMeters,
        boolean completed) {
}
