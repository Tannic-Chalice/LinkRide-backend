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
     * (§6.1/§8.5).
     */
    public Optional<PassengerEtaView> etaForBooking(RouteGeometry geometry, RouteProgress progress, Booking booking, Instant now) {
        StopType stop;
        double targetLat;
        double targetLng;

        if (booking.getStatus() == BookingStatus.ACCEPTED) {
            stop = StopType.PICKUP;
            targetLat = booking.getPickupLat();
            targetLng = booking.getPickupLng();
        } else if (booking.getStatus() == BookingStatus.CHECKED_IN) {
            stop = StopType.DROPOFF;
            targetLat = booking.getDropLat();
            targetLng = booking.getDropLng();
        } else {
            return Optional.empty();
        }

        NearestPointOnRoute targetProjection = geometry.nearestPoint(targetLat, targetLng);
        Instant eta = etaTo(geometry, progress, targetProjection.cumulativeDistanceMeters(), now);

        return Optional.of(new PassengerEtaView(booking.getBookingId(), stop, eta, ArrivalState.EN_ROUTE));
    }
}
