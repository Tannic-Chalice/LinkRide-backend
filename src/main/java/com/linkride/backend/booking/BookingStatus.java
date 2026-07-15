package com.linkride.backend.booking;

/**
 * Booking lifecycle (Phase 3).
 *
 * <p>{@code EXPIRED} is distinct from {@code REJECTED}: it means the driver never made a
 * decision before the ride moved past the point where one could be made ({@code RideService}
 * cascades stale {@code PENDING} bookings to this state when the driver explicitly starts or
 * cancels the ride) — not that the driver declined the request.</p>
 */
public enum BookingStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    EXPIRED,
    COMPLETED
}
