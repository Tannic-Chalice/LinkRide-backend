package com.linkride.backend.route;

/**
 * Lifecycle of route generation for a ride — distinct from whether the ride itself is valid.
 * A ride is always created regardless of this state; only route enrichment is affected.
 */
public enum RouteGenerationState {
    /** No route yet — never attempted, or a transient failure that a future retry may resolve. */
    PENDING,
    /** Route successfully computed and stored. */
    READY,
    /** The provider gave a definitive answer that this exact pickup/waypoints/destination cannot be routed. */
    UNROUTABLE
}
