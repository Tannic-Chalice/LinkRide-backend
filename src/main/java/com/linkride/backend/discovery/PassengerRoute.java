package com.linkride.backend.discovery;

import com.linkride.backend.route.geometry.RouteGeometry;
import com.linkride.backend.route.RouteGenerationState;
import lombok.Builder;
import lombok.Data;

/**
 * The passenger's own origin→destination route for a single search — computed once by
 * {@link PassengerRouteService} (via {@link PassengerRouteMapper}) and threaded through the rest
 * of the discovery pipeline (starting with {@link CandidateGenerationService}) so downstream
 * stages never need to recompute or re-fetch it themselves.
 *
 * <p>Deliberately a plain data holder — no conversion logic lives here. See
 * {@link PassengerRouteMapper} for how this is built from a {@link com.linkride.backend.route.RouteResult}.
 * That separation matters because this mapping only grows more involved as later milestones add
 * shared-corridor data — keeping the domain object pure means that growth has a home that isn't
 * this class.</p>
 *
 * <p>Distinct from {@link PassengerRouteDto} the same way {@link com.linkride.backend.location.GeoPoint}
 * is distinct from {@link com.linkride.backend.location.GeoPointDto} elsewhere in the backend:
 * this is the internal pipeline-facing shape, the DTO is the API-facing one — {@code geometry}
 * in particular is never exposed to the client, it's purely an aid for corridor computation
 * (Phase 2B.5).</p>
 */
@Data
@Builder
public class PassengerRoute {

    private String polyline;
    private Integer distanceMeters;
    private Integer durationSeconds;
    private RouteGenerationState routeGenerationState;

    /** Decoded, cumulative-distance/time-annotated geometry — null unless {@code routeGenerationState == READY}. */
    private RouteGeometry geometry;
}
