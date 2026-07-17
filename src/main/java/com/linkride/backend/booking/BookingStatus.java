package com.linkride.backend.booking;

/**
 * Booking lifecycle (Phase 3, extended in Phase 4).
 *
 * <p>{@code EXPIRED} is distinct from {@code REJECTED}: it means the driver never made a
 * decision before the ride moved past the point where one could be made ({@code RideService}
 * cascades stale {@code PENDING} bookings to this state when the driver explicitly starts or
 * cancels the ride) — not that the driver declined the request.</p>
 *
 * <p>{@code NO_SHOW} is the {@code CHECKED_IN} analogue of {@code EXPIRED}: the driver did decide
 * ({@code ACCEPTED}), but the passenger never boarded (never reached {@code CHECKED_IN}) before
 * the driver started the ride. Kept distinct from {@code EXPIRED} because it represents a
 * different failure mode — a driver who forgot to decide vs. a passenger who didn't show up —
 * worth telling apart in the data.</p>
 */
public enum BookingStatus {
    PENDING,
    ACCEPTED,
    CHECKED_IN,
    REJECTED,
    CANCELLED,
    EXPIRED,
    NO_SHOW,
    COMPLETED
}
