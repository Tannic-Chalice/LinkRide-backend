package com.linkride.backend.ride;

import com.linkride.backend.location.GeoPointDto;
import com.linkride.backend.location.GeoPoints;
import com.linkride.backend.route.RouteGenerationState;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RideResponse {

    private UUID rideId;
    private UUID driverId;
    private UUID vehicleId;

    private GeoPointDto pickup;
    private GeoPointDto destination;
    private List<GeoPointDto> waypoints;

    private OffsetDateTime departureTime;

    private Integer offeredSeats;
    private Integer totalSeats;
    private Integer availableSeats;

    private RepeatSchedule repeatSchedule;
    private RidePreferencesDto preferences;

    private String routePolyline;
    private Integer estimatedDistanceMeters;
    private Integer estimatedDurationSeconds;
    private RouteGenerationState routeGenerationState;

    private RideStatus status;
    private OffsetDateTime createdAt;

    public static RideResponse from(Ride ride, List<RideWaypoint> waypoints) {
        RidePreferencesDto preferences = new RidePreferencesDto();
        preferences.setQuietRide(ride.getQuietRide());
        preferences.setAc(ride.getAcPreference());
        preferences.setTrunkSpace(ride.getTrunkSpace());

        return RideResponse.builder()
                .rideId(ride.getRideId())
                .driverId(ride.getDriver().getId())
                .vehicleId(ride.getVehicle().getCarId())
                .pickup(GeoPoints.toDto(ride.getPickup()))
                .destination(GeoPoints.toDto(ride.getDestination()))
                .waypoints(waypoints.stream()
                        .map(w -> GeoPoints.toDto(w.getLocation()))
                        .toList())
                .departureTime(ride.getDepartureTime())
                .offeredSeats(ride.getOfferedSeats())
                .totalSeats(ride.getTotalSeats())
                .availableSeats(ride.getAvailableSeats())
                .repeatSchedule(ride.getRepeatSchedule())
                .preferences(preferences)
                .routePolyline(ride.getRoutePolyline())
                .estimatedDistanceMeters(ride.getEstimatedDistanceMeters())
                .estimatedDurationSeconds(ride.getEstimatedDurationSeconds())
                .routeGenerationState(ride.getRouteGenerationState())
                .status(ride.getStatus())
                .createdAt(ride.getCreatedAt())
                .build();
    }
}
