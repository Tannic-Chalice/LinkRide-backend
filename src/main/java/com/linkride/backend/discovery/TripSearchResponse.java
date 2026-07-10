package com.linkride.backend.discovery;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response envelope for {@code POST /api/rides/search}.
 *
 * <p>{@code passengerRoute} is always populated (Phase 2B.2). {@code matches} is the ranked,
 * top-K-truncated set of rides surviving shared-corridor computation, hard-constraint validation,
 * and scoring (Phase 2B.5–2B.8) — see {@link RideMatchDto} for the full pipeline and what each
 * field guarantees. Order is meaningful: best match first.</p>
 */
@Data
@Builder
public class TripSearchResponse {

    private PassengerRouteDto passengerRoute;
    private List<RideMatchDto> matches;
}
