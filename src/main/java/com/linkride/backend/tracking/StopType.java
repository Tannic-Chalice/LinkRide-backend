package com.linkride.backend.tracking;

/**
 * The two points on a route a booking can arrive at. Keys
 * {@link LiveRideState#getArrivalHighWaterMark()} per (booking, stop) — see
 * backend/docs/phase-7-live-trip-management.md §10.
 */
public enum StopType {
    PICKUP,
    DROPOFF
}
