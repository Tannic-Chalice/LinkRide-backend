package com.linkride.backend.tracking;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory-only live state for a ride currently {@code IN_PROGRESS} — deliberately minimal
 * (see backend/docs/phase-7-live-trip-management.md §6.1). Not a JPA entity and never persisted
 * to Postgres. Everything else that live tracking needs — driver identity, per-booking
 * pickup/drop route-distance projections — is recomputed from the already-necessary
 * {@code Ride}/{@code Booking} fetch on every request instead of cached here, so this type only
 * carries the two things that genuinely can't be reconstructed: the latest GPS fix, and the
 * monotonic arrival dedup state.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LiveRideState {

    private UUID rideId;

    private double lastLatitude;
    private double lastLongitude;
    private Instant lastUpdatedAt;

    /**
     * Per-(booking, stop) arrival high-water-mark — the one piece of derived state that can't be
     * recomputed from a single GPS fix without risking a regression (§10). Never read or written
     * concurrently for the same ride outside of {@link LiveRideStateStore#save}.
     */
    private Map<UUID, EnumMap<StopType, ArrivalState>> arrivalHighWaterMark = new HashMap<>();
}
