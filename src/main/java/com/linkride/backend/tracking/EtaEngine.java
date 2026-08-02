package com.linkride.backend.tracking;

import com.linkride.backend.booking.Booking;
import com.linkride.backend.booking.BookingStatus;
import com.linkride.backend.route.geometry.NearestPointOnRoute;
import com.linkride.backend.route.geometry.RouteGeometry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Route progress -&gt; per-stop ETA (§9). Deliberately separate from
 * {@link com.linkride.backend.route.geometry.RouteEtaCalculator#etaAt}, which is anchored to a
 * ride's <em>planned</em> {@code departureTime} for pre-ride matching/scoring — this answers a
 * different question, "starting from right now, at the driver's actual current position, when
 * will they reach point X" (ADR-6). Same proportional-interpolation math, re-anchored to "now."
 */
@Component
public class EtaEngine {

    /** ETA to an arbitrary point of progress along the route, anchored to {@code now}. */
    public Instant etaTo(RouteGeometry geometry, RouteProgress progress, double targetCumulativeDistanceMeters, Instant now) {
        double totalDistance = geometry.totalDistanceMeters();
        double remainingToTarget = targetCumulativeDistanceMeters - progress.driverCumulativeDistanceMeters();
        double remainingSeconds = totalDistance == 0
                ? 0
                : geometry.totalDurationSeconds() * (remainingToTarget / totalDistance);
        remainingSeconds = Math.max(0, remainingSeconds);
        return now.plusSeconds(Math.round(remainingSeconds));
    }

    /**
     * Exactly one ETA per passenger (§9): {@code ACCEPTED} -&gt; ETA to pickup, {@code CHECKED_IN}
     * -&gt; ETA to drop-off, any other status -&gt; excluded from the snapshot entirely. The
     * booking's pickup/drop projection is recomputed fresh here every call, never memoized
     * (§6.1/§8.5). {@code arrivalState} defaults to {@link ArrivalState#EN_ROUTE} —
     * {@code LocationUpdateService} overwrites it with the real value from the ride's
     * {@code LiveRideState} once {@link ArrivalDetectionService} has evaluated it (§10).
     */
    public Optional<PassengerEtaView> etaForBooking(RouteGeometry geometry, RouteProgress progress, Booking booking, Instant now) {
        return resolveTarget(geometry, booking).map(target -> {
            Instant eta = etaTo(geometry, progress, target.targetCumulativeDistanceMeters(), now);
            return new PassengerEtaView(booking.getBookingId(), target.stop(), eta, ArrivalState.EN_ROUTE);
        });
    }

    /**
     * Which stop a booking is currently heading to, and where that stop projects onto the route
     * — {@code ACCEPTED} -&gt; pickup, {@code CHECKED_IN} -&gt; drop-off, any other status -&gt;
     * empty. Shared with {@link ArrivalDetectionService} so both agree on the same definition of
     * "active stop" (§10).
     */
    public Optional<BookingTarget> resolveTarget(RouteGeometry geometry, Booking booking) {
        if (booking.getStatus() == BookingStatus.ACCEPTED) {
            NearestPointOnRoute projection = geometry.nearestPoint(booking.getPickupLat(), booking.getPickupLng());
            return Optional.of(new BookingTarget(StopType.PICKUP, projection.cumulativeDistanceMeters()));
        }
        if (booking.getStatus() == BookingStatus.CHECKED_IN) {
            NearestPointOnRoute projection = geometry.nearestPoint(booking.getDropLat(), booking.getDropLng());
            return Optional.of(new BookingTarget(StopType.DROPOFF, projection.cumulativeDistanceMeters()));
        }
        return Optional.empty();
    }
}
