package com.linkride.backend.route;

import lombok.Getter;

/**
 * Result of a route computation attempt. {@code polyline}/{@code distanceMeters}/{@code durationSeconds}
 * are only populated when {@code state == READY}; {@code failureReason} is only populated otherwise.
 */
@Getter
public final class RouteResult {

    private final RouteGenerationState state;
    private final String polyline;
    private final Integer distanceMeters;
    private final Integer durationSeconds;
    private final RouteFailureReason failureReason;

    private RouteResult(RouteGenerationState state, String polyline, Integer distanceMeters,
                         Integer durationSeconds, RouteFailureReason failureReason) {
        this.state = state;
        this.polyline = polyline;
        this.distanceMeters = distanceMeters;
        this.durationSeconds = durationSeconds;
        this.failureReason = failureReason;
    }

    public static RouteResult ready(String polyline, int distanceMeters, int durationSeconds) {
        return new RouteResult(RouteGenerationState.READY, polyline, distanceMeters, durationSeconds, null);
    }

    public static RouteResult pending(RouteFailureReason failureReason) {
        return new RouteResult(RouteGenerationState.PENDING, null, null, null, failureReason);
    }

    public static RouteResult unroutable(RouteFailureReason failureReason) {
        return new RouteResult(RouteGenerationState.UNROUTABLE, null, null, null, failureReason);
    }
}
