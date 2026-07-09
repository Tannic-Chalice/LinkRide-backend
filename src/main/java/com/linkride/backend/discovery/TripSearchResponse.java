package com.linkride.backend.discovery;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response envelope for {@code POST /api/rides/search}.
 *
 * <p>{@code passengerRoute} is always populated (Phase 2B.2). {@code matches} is the set of
 * rides surviving shared-corridor computation (Phase 2B.5) — see {@link RideMatchDto} for what
 * that does and doesn't yet guarantee. Order is not meaningful until ranking (Phase 2B.8)
 * exists.</p>
 */
@Data
@Builder
public class TripSearchResponse {

    private PassengerRouteDto passengerRoute;
    private List<RideMatchDto> matches;
}
