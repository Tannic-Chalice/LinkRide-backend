package com.linkride.backend.tracking;

/**
 * Per-(booking, stop) arrival progress (see backend/docs/phase-7-live-trip-management.md §10).
 * Monotonic — the state stored for a given (booking, stop) never regresses to an earlier value,
 * so GPS jitter near a threshold can't flap a passenger back down and re-fire a notification.
 */
public enum ArrivalState {
    EN_ROUTE,
    APPROACHING,
    ARRIVED
}
