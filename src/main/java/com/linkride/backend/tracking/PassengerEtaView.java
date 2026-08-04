package com.linkride.backend.tracking;

import java.time.Instant;
import java.util.UUID;

/**
 * One passenger's live ETA (§9) and their current arrival state for that stop (§10). Broadcast
 * to the passenger over {@code /user/queue/ride-progress} and folded into the driver's
 * {@link RideProgressSnapshot}. {@code arrivalState} defaults to {@link ArrivalState#EN_ROUTE};
 * {@code ArrivalDetectionService} (§10) is the only thing that ever advances it.
 */
public record PassengerEtaView(
        UUID bookingId,
        StopType stop,
        Instant eta,
        ArrivalState arrivalState) {
}
