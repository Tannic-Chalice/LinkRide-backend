package com.linkride.backend.discovery.corridor;

import com.linkride.backend.route.geometry.RoutePoint;

/**
 * A maximal contiguous run of the passenger's own route that lies within walking distance of a
 * driver's route (design doc §5.1) — the shared corridor's primary object. {@code entryPoint}/
 * {@code exitPoint} are points on the *passenger's* route (in passenger-route order — entry
 * always precedes exit, since cumulative distance is monotonic along a route); pickup/drop are
 * derived from these by projecting onto the driver's route (§5.2), not stored here.
 */
public record CorridorRun(RoutePoint entryPoint, RoutePoint exitPoint, double overlapFraction) {
}
