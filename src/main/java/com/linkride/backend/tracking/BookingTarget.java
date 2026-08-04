package com.linkride.backend.tracking;

/**
 * A booking's currently-active stop and where it projects onto the route (§9/§10) — the pickup
 * point while {@code ACCEPTED}, the drop-off point once {@code CHECKED_IN}. Shared by
 * {@link EtaEngine} (ETA math) and {@link ArrivalDetectionService} (arrival state machine) so
 * both agree on exactly one definition of "which stop is this booking heading to right now."
 */
public record BookingTarget(StopType stop, double targetCumulativeDistanceMeters) {
}
