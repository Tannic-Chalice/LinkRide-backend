package com.linkride.backend.route;

/**
 * Why route generation did not reach {@link RouteGenerationState#READY}. Not surfaced to
 * end users — for logging, observability, and future automated retry strategies only.
 */
public enum RouteFailureReason {
    TIMEOUT,
    CONNECTION_FAILURE,
    ZERO_RESULTS,
    NOT_FOUND,
    INVALID_REQUEST,
    MAX_WAYPOINTS_EXCEEDED,
    REQUEST_DENIED,
    OVER_QUERY_LIMIT,
    UNKNOWN_ERROR
}
