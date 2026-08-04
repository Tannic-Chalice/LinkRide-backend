package com.linkride.backend.tracking;

import com.linkride.backend.booking.Booking;
import com.linkride.backend.booking.BookingRepository;
import com.linkride.backend.booking.BookingStatus;
import com.linkride.backend.ride.Ride;
import com.linkride.backend.ride.RideRepository;
import com.linkride.backend.ride.RideStatus;
import com.linkride.backend.ride.RideWaypoint;
import com.linkride.backend.ride.RideWaypointRepository;
import com.linkride.backend.route.geometry.RouteGeometry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates §11.4's flow: validate the incoming GPS fix, update {@link LiveRideStateStore},
 * compute route progress and per-passenger ETAs, run arrival detection, return the resulting
 * {@link RideProgressSnapshot} to the driver, and — after that response is already on its way —
 * broadcast it and each passenger's ETA over WebSocket (§11.3/ADR-9: broadcasting never blocks
 * the triggering request).
 */
@Service
@RequiredArgsConstructor
public class LocationUpdateService {

    private final RideRepository rideRepository;
    private final RideWaypointRepository rideWaypointRepository;
    private final BookingRepository bookingRepository;
    private final LiveRideStateStore liveRideStateStore;
    private final GpsFixValidator gpsFixValidator;
    private final RouteProgressEngine routeProgressEngine;
    private final EtaEngine etaEngine;
    private final ArrivalDetectionService arrivalDetectionService;
    private final LiveTrackingBroadcaster liveTrackingBroadcaster;

    /**
     * {@code POST /rides/{rideId}/location} (§12). Maps {@link GpsValidationOutcome} to its
     * documented response (§7.2): {@code ACCEPTED} persists the fix, runs arrival detection
     * (§10), and returns a fresh snapshot; {@code IGNORED_DUPLICATE} returns the last known
     * snapshot unchanged (no re-evaluation); every {@code REJECTED_*} outcome becomes a 422
     * carrying the outcome name as the reason.
     */
    public RideProgressSnapshot updateLocation(UUID driverId, UUID rideId, LocationUpdateRequest request) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));

        if (!ride.getDriver().getId().equals(driverId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not drive this ride");
        }
        if (ride.getStatus() != RideStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ride is not in progress");
        }
        RouteGeometry geometry = ride.getRouteGeometry();
        if (geometry == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ride has no route geometry yet");
        }

        Instant now = Instant.now();
        Optional<LiveRideState> previous = liveRideStateStore.get(rideId);
        GpsValidationOutcome outcome = gpsFixValidator.validate(previous, request, now);

        if (outcome == GpsValidationOutcome.IGNORED_DUPLICATE) {
            LiveRideState lastKnown = previous.orElseThrow(
                    () -> new IllegalStateException("IGNORED_DUPLICATE implies a previous LiveRideState exists"));
            return buildSnapshot(ride, geometry, lastKnown);
        }
        if (outcome != GpsValidationOutcome.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, outcome.name());
        }

        LiveRideState state = new LiveRideState(
                rideId,
                request.getLatitude(),
                request.getLongitude(),
                request.getRecordedAt(),
                previous.map(LiveRideState::getArrivalHighWaterMark).orElseGet(HashMap::new));

        List<RideWaypoint> waypoints = rideWaypointRepository.findByRide_RideIdOrderBySequenceAsc(rideId);
        RouteProgress progress = routeProgressEngine.computeProgress(
                geometry, state.getLastLatitude(), state.getLastLongitude(), waypoints);
        List<Booking> activeBookings = bookingRepository.findBoardingCandidatesForRide(rideId);

        arrivalDetectionService.evaluate(geometry, progress, activeBookings, state);
        liveRideStateStore.save(rideId, state);

        RideProgressSnapshot snapshot = buildSnapshot(ride, geometry, state, progress, activeBookings);
        broadcast(driverId, activeBookings, snapshot);
        return snapshot;
    }

    /**
     * Fans the fresh snapshot out over WebSocket (§11.3): the driver gets the full snapshot,
     * each {@code CHECKED_IN} passenger gets just their own {@link PassengerEtaView}. Every call
     * is {@code @Async} (§4/ADR-9) — this method itself returns immediately regardless of any
     * passenger's connection health.
     */
    private void broadcast(UUID driverId, List<Booking> activeBookings, RideProgressSnapshot snapshot) {
        liveTrackingBroadcaster.pushToUser(driverId, snapshot);

        Map<UUID, PassengerEtaView> etaByBooking = snapshot.passengerEtas().stream()
                .collect(Collectors.toMap(PassengerEtaView::bookingId, view -> view));
        for (Booking booking : activeBookings) {
            if (booking.getStatus() == BookingStatus.CHECKED_IN) {
                PassengerEtaView view = etaByBooking.get(booking.getBookingId());
                if (view != null) {
                    liveTrackingBroadcaster.pushToUser(booking.getPassenger().getId(), view);
                }
            }
        }
    }

    /**
     * {@code GET /rides/{rideId}/tracking} (§12) — the pre-connect initial load and offline/
     * polling fallback. 403s if the caller is neither the ride's driver nor a checked-in
     * passenger on it; 404s if no GPS fix has ever been recorded for this ride. Reports the
     * arrival state as of the last processed fix — does not re-evaluate it.
     */
    public RideProgressSnapshot getSnapshot(UUID callerId, UUID rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));

        boolean isDriver = ride.getDriver().getId().equals(callerId);
        boolean isCheckedInPassenger = !isDriver && bookingRepository
                .existsByRide_RideIdAndPassenger_IdAndStatus(rideId, callerId, BookingStatus.CHECKED_IN);
        if (!isDriver && !isCheckedInPassenger) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You are not the driver or a checked-in passenger on this ride");
        }

        RouteGeometry geometry = ride.getRouteGeometry();
        if (geometry == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No location has been recorded for this ride yet");
        }
        LiveRideState state = liveRideStateStore.get(rideId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No location has been recorded for this ride yet"));

        return buildSnapshot(ride, geometry, state);
    }

    /** Recomputes progress and active bookings fresh — used by the duplicate/GET paths, which have neither on hand. */
    private RideProgressSnapshot buildSnapshot(Ride ride, RouteGeometry geometry, LiveRideState state) {
        List<RideWaypoint> waypoints = rideWaypointRepository.findByRide_RideIdOrderBySequenceAsc(ride.getRideId());
        RouteProgress progress = routeProgressEngine.computeProgress(
                geometry, state.getLastLatitude(), state.getLastLongitude(), waypoints);
        List<Booking> activeBookings = bookingRepository.findBoardingCandidatesForRide(ride.getRideId());
        return buildSnapshot(ride, geometry, state, progress, activeBookings);
    }

    private RideProgressSnapshot buildSnapshot(
            Ride ride, RouteGeometry geometry, LiveRideState state, RouteProgress progress, List<Booking> activeBookings) {

        Instant now = Instant.now();
        List<PassengerEtaView> passengerEtas = activeBookings.stream()
                .map((Booking booking) -> etaEngine.etaForBooking(geometry, progress, booking, now)
                        .map(view -> withCurrentArrivalState(view, state)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        return new RideProgressSnapshot(
                ride.getRideId(),
                state.getLastLatitude(),
                state.getLastLongitude(),
                state.getLastUpdatedAt(),
                progress.driverCumulativeDistanceMeters(),
                progress.remainingDistanceMeters(),
                progress.remainingDurationSeconds(),
                progress.onRoute(),
                progress.waypoints(),
                passengerEtas);
    }

    /** Overwrites EtaEngine's EN_ROUTE default with the booking's real, stored arrival state (§10). */
    private PassengerEtaView withCurrentArrivalState(PassengerEtaView view, LiveRideState state) {
        EnumMap<StopType, ArrivalState> perStop = state.getArrivalHighWaterMark().get(view.bookingId());
        ArrivalState arrivalState = perStop == null ? ArrivalState.EN_ROUTE : perStop.getOrDefault(view.stop(), ArrivalState.EN_ROUTE);
        return new PassengerEtaView(view.bookingId(), view.stop(), view.eta(), arrivalState);
    }
}
