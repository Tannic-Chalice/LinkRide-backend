package com.linkride.backend.ride;

import com.linkride.backend.booking.Booking;
import com.linkride.backend.booking.BookingRepository;
import com.linkride.backend.booking.BookingStatus;
import com.linkride.backend.entity.User;
import com.linkride.backend.entity.Vehicle;
import com.linkride.backend.location.GeoPointDto;
import com.linkride.backend.location.GeoPoints;
import com.linkride.backend.notification.NotificationCategory;
import com.linkride.backend.notification.NotificationEvent;
import com.linkride.backend.repository.UserRepository;
import com.linkride.backend.repository.VehicleRepository;
import com.linkride.backend.route.RouteGenerationState;
import com.linkride.backend.route.RouteProvider;
import com.linkride.backend.route.RouteResult;
import com.linkride.backend.route.geometry.RouteGeometryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;

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

    /**
     * Cancels a SCHEDULED ride. Cascades: every {@code PENDING}/{@code ACCEPTED}/{@code
     * CHECKED_IN} booking on it is cancelled too (driver-initiated) — seats aren't released, the
     * ride is dead so its {@code available_seats} no longer matters. Reaching {@code CHECKED_IN}
     * doesn't happen until after the driver has already opened the boarding screen, but the ride
     * itself is still {@code SCHEDULED} the whole time boarding is in progress (Phase 4), so a
     * driver cancelling mid-boarding is a real, reachable case, not just a defensive check.
     */
    @Transactional
    public RideResponse cancelRide(UUID driverId, UUID rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));

        if (!ride.getDriver().getId().equals(driverId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not drive this ride");
        }

        if (rideRepository.cancelIfScheduled(rideId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ride cannot be cancelled from its current state");
        }

        // Snapshot before the bulk cascade below -- a bulk @Modifying update doesn't return the
        // rows it touched, so the passengers to notify must be read first (Phase 6 — see
        // backend/docs/phase-6-notifications.md §7).
        List<Booking> affectedBookings = bookingRepository.findByRide_RideId(rideId).stream()
                .filter(b -> b.getStatus() == BookingStatus.PENDING
                        || b.getStatus() == BookingStatus.ACCEPTED
                        || b.getStatus() == BookingStatus.CHECKED_IN)
                .toList();

        ride.setStatus(RideStatus.CANCELLED);
        bookingRepository.cancelAllActiveBookingsForRide(rideId);

        for (Booking booking : affectedBookings) {
            eventPublisher.publishEvent(new NotificationEvent(
                    booking.getPassenger().getId(), NotificationCategory.RIDE, "RIDE_CANCELLED",
                    "Ride cancelled",
                    "Your ride to " + ride.getDestination().getName() + " was cancelled by the driver.",
                    "BOOKING", booking.getBookingId()));
        }

        return RideResponse.from(ride, rideWaypointRepository.findByRide_RideIdOrderBySequenceAsc(rideId));
    }

    /**
     * Starts a SCHEDULED ride. Cascades two ways: any request still {@code PENDING} at this
     * moment expires — the driver never decided it in time, distinct from an explicit {@code
     * REJECTED}. Any booking still {@code ACCEPTED} — decided, but never boarded — becomes {@code
     * NO_SHOW} (Phase 4.6): the driver's own boarding screen is where check-in happens, and once
     * the driver swipes "Start Ride" that window is over. Never triggered by {@code
     * departureTime} passing; only this explicit call moves the ride, and with it, its stale
     * requests.
     */
    @Transactional
    public RideResponse startRide(UUID driverId, UUID rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));

        if (!ride.getDriver().getId().equals(driverId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not drive this ride");
        }

        if (rideRepository.startIfScheduled(rideId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ride cannot be started from its current state");
        }

        ride.setStatus(RideStatus.IN_PROGRESS);
        bookingRepository.expireAllPendingBookingsForRide(rideId);
        bookingRepository.markNoShowAllAcceptedBookingsForRide(rideId);

        // Only CHECKED_IN survives this cascade (every ACCEPTED booking above just became
        // NO_SHOW) -- those are the passengers actually still on the ride. EXPIRED/NO_SHOW get no
        // notification in this phase (architecture doc §7, deliberate scope decision).
        List<Booking> checkedInBookings = bookingRepository.findByRide_RideIdAndStatus(rideId, BookingStatus.CHECKED_IN);
        for (Booking booking : checkedInBookings) {
            eventPublisher.publishEvent(new NotificationEvent(
                    booking.getPassenger().getId(), NotificationCategory.RIDE, "RIDE_STARTED",
                    "Ride started",
                    "Your ride to " + ride.getDestination().getName() + " has started.",
                    "BOOKING", booking.getBookingId()));
        }

        return RideResponse.from(ride, rideWaypointRepository.findByRide_RideIdOrderBySequenceAsc(rideId));
    }

    /**
     * Completes an IN_PROGRESS ride. Cascades: every {@code CHECKED_IN} booking completes with it
     * (Phase 4.6 — previously {@code ACCEPTED}, back when boarding didn't exist as its own state;
     * by the time a ride reaches completion, {@code startRide}'s own cascade has already resolved
     * every {@code ACCEPTED} booking to either {@code CHECKED_IN} or {@code NO_SHOW}).
     */
    @Transactional
    public RideResponse completeRide(UUID driverId, UUID rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));

        if (!ride.getDriver().getId().equals(driverId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not drive this ride");
        }

        if (rideRepository.completeIfInProgress(rideId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ride cannot be completed from its current state");
        }

        // Snapshot before the bulk complete below -- these are exactly the rows it's about to
        // change, and a bulk @Modifying update doesn't return them.
        List<Booking> checkedInBookings = bookingRepository.findByRide_RideIdAndStatus(rideId, BookingStatus.CHECKED_IN);

        ride.setStatus(RideStatus.COMPLETED);
        bookingRepository.completeAllCheckedInBookingsForRide(rideId);

        for (Booking booking : checkedInBookings) {
            eventPublisher.publishEvent(new NotificationEvent(
                    booking.getPassenger().getId(), NotificationCategory.RIDE, "RIDE_COMPLETED",
                    "Ride completed",
                    "Your ride to " + ride.getDestination().getName() + " has been completed.",
                    "BOOKING", booking.getBookingId()));
        }

        return RideResponse.from(ride, rideWaypointRepository.findByRide_RideIdOrderBySequenceAsc(rideId));
    }
}
