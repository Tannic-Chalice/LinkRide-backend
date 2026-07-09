package com.linkride.backend.ride;

import com.linkride.backend.entity.User;
import com.linkride.backend.entity.Vehicle;
import com.linkride.backend.location.GeoPointDto;
import com.linkride.backend.location.GeoPoints;
import com.linkride.backend.repository.UserRepository;
import com.linkride.backend.repository.VehicleRepository;
import com.linkride.backend.route.RouteGenerationState;
import com.linkride.backend.route.RouteProvider;
import com.linkride.backend.route.RouteResult;
import com.linkride.backend.route.geometry.RouteGeometryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RideService {

    private final RideRepository rideRepository;
    private final RideWaypointRepository rideWaypointRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final RouteProvider routeProvider;
    private final RouteGeometryBuilder routeGeometryBuilder;

    /**
     * Creates a ride for the authenticated driver.
     *
     * <p>Business rules enforced:</p>
     * <ul>
     *   <li>Driver, vehicle, total/available seats are resolved server-side — never
     *       trusted from the request.</li>
     *   <li>Eligible to drive only if the driver owns an active, admin-verified vehicle.</li>
     *   <li>{@code offeredSeats} must not exceed the resolved vehicle's capacity.</li>
     *   <li>Pickup and destination must differ.</li>
     * </ul>
     */
    @Transactional
    public RideResponse createRide(UUID driverId, RideCreateRequest request) {
        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Authenticated user not found in the system"));

        Vehicle vehicle = vehicleRepository.findByOwnerIdAndIsActiveTrueAndIsVerifiedTrue(driverId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "No active, verified vehicle found. Register and verify a vehicle before creating rides."));

        if (request.getOfferedSeats() > vehicle.getNoOfSeats()) {
            throw new IllegalArgumentException(
                    "Offered seats (" + request.getOfferedSeats()
                            + ") exceeds vehicle capacity (" + vehicle.getNoOfSeats() + ")");
        }

        if (isSameLocation(request.getPickup(), request.getDestination())) {
            throw new IllegalArgumentException("Pickup and destination must not be the same location");
        }

        List<GeoPointDto> waypointDtos = request.getWaypoints() != null ? request.getWaypoints() : List.of();
        RouteResult route = routeProvider.computeRoute(request.getPickup(), waypointDtos, request.getDestination());

        Ride ride = new Ride();
        ride.setDriver(driver);
        ride.setVehicle(vehicle);
        ride.setPickup(GeoPoints.fromDto(request.getPickup()));
        ride.setDestination(GeoPoints.fromDto(request.getDestination()));
        ride.setDepartureTime(request.getDepartureTime());
        ride.setOfferedSeats(request.getOfferedSeats());
        ride.setTotalSeats(vehicle.getNoOfSeats());
        ride.setAvailableSeats(request.getOfferedSeats());
        ride.setRepeatSchedule(request.getRepeatSchedule());

        if (request.getPreferences() != null) {
            ride.setQuietRide(request.getPreferences().getQuietRide());
            ride.setAcPreference(request.getPreferences().getAc());
            ride.setTrunkSpace(request.getPreferences().getTrunkSpace());
        }

        ride.setRoutePolyline(route.getPolyline());
        ride.setEstimatedDistanceMeters(route.getDistanceMeters());
        ride.setEstimatedDurationSeconds(route.getDurationSeconds());
        ride.setRouteGenerationState(route.getState());

        if (route.getState() == RouteGenerationState.READY) {
            ride.setRouteGeometry(routeGeometryBuilder.build(
                    route.getPolyline(), route.getDistanceMeters(), route.getDurationSeconds()));
        }

        Ride savedRide = rideRepository.save(ride);

        List<RideWaypoint> savedWaypoints = new ArrayList<>();
        int sequence = 1;
        for (GeoPointDto waypointDto : waypointDtos) {
            RideWaypoint waypoint = new RideWaypoint();
            waypoint.setRide(savedRide);
            waypoint.setSequence(sequence++);
            waypoint.setLocation(GeoPoints.fromDto(waypointDto));
            savedWaypoints.add(waypoint);
        }
        if (!savedWaypoints.isEmpty()) {
            savedWaypoints = rideWaypointRepository.saveAll(savedWaypoints);
        }

        return RideResponse.from(savedRide, savedWaypoints);
    }

    private boolean isSameLocation(GeoPointDto a, GeoPointDto b) {
        return a.getLatitude().equals(b.getLatitude()) && a.getLongitude().equals(b.getLongitude());
    }
}
