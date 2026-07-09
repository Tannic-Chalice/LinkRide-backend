package com.linkride.backend.discovery;

import com.linkride.backend.route.RouteGenerationState;
import lombok.Builder;
import lombok.Data;

/**
 * The passenger's own origin→destination route for this search — always populated (unlike
 * {@link TripSearchResponse#getMatches()}), since {@link PassengerRouteService} computes it on
 * every call. Mirrors the route fields already exposed on
 * {@link com.linkride.backend.ride.RideResponse} for a driver's ride.
 */
@Data
@Builder
public class PassengerRouteDto {

    private String polyline;
    private Integer distanceMeters;
    private Integer durationSeconds;
    private RouteGenerationState routeGenerationState;

    public static PassengerRouteDto from(PassengerRoute passengerRoute) {
        return PassengerRouteDto.builder()
                .polyline(passengerRoute.getPolyline())
                .distanceMeters(passengerRoute.getDistanceMeters())
                .durationSeconds(passengerRoute.getDurationSeconds())
                .routeGenerationState(passengerRoute.getRouteGenerationState())
                .build();
    }
}
