package com.linkride.backend.tracking;

import com.linkride.backend.booking.Booking;
import com.linkride.backend.notification.NotificationCategory;
import com.linkride.backend.notification.NotificationEvent;
import com.linkride.backend.route.geometry.RouteGeometry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-(booking, stop) arrival state machine (§10): {@code EN_ROUTE -> APPROACHING -> ARRIVED},
 * driven by route-distance-remaining against {@link TrackingProperties}' two radii — never raw
 * haversine (§10). Monotonic and deduplicated via {@link LiveRideState#getArrivalHighWaterMark()}:
 * a transition only ever advances, never regresses, so GPS jitter near a threshold can't flap a
 * passenger back down and re-fire a notification already sent.
 *
 * <p>Only a newly-reached {@code APPROACHING}/{@code ARRIVED} fires a {@link NotificationEvent}
 * (extending the existing Phase 6 pipeline — zero new notification infrastructure);
 * {@code EN_ROUTE} is the silent default and never notifies. This never transitions
 * {@code BookingStatus} or {@code RideStatus} (§4/ADR-4) — proximity is communicated to the
 * passenger, never enforced as a state change.</p>
 */
@Service
@RequiredArgsConstructor
public class ArrivalDetectionService {

    private final EtaEngine etaEngine;
    private final TrackingProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Evaluates every active booking's arrival progress against the current fix, advances
     * {@code state.getArrivalHighWaterMark()} in place for any (booking, stop) that newly
     * progressed, and publishes a {@link NotificationEvent} for each newly-reached transition.
     * The caller is responsible for persisting {@code state} afterward — this method only
     * mutates the in-memory object handed to it.
     *
     * @return the resulting arrival state per booking, keyed by {@code bookingId}
     */
    public Map<UUID, ArrivalState> evaluate(
            RouteGeometry geometry, RouteProgress progress, List<Booking> activeBookings, LiveRideState state) {

        Map<UUID, ArrivalState> resolved = new HashMap<>();

        for (Booking booking : activeBookings) {
            Optional<BookingTarget> target = etaEngine.resolveTarget(geometry, booking);
            if (target.isEmpty()) {
                continue;
            }

            StopType stop = target.get().stop();
            double distanceToTargetMeters = Math.max(
                    0, target.get().targetCumulativeDistanceMeters() - progress.driverCumulativeDistanceMeters());
            ArrivalState observedState = classify(distanceToTargetMeters);

            EnumMap<StopType, ArrivalState> perStop = state.getArrivalHighWaterMark()
                    .computeIfAbsent(booking.getBookingId(), id -> new EnumMap<>(StopType.class));
            ArrivalState previousState = perStop.getOrDefault(stop, ArrivalState.EN_ROUTE);
            ArrivalState effectiveState = maxOf(previousState, observedState);

            if (effectiveState != previousState) {
                perStop.put(stop, effectiveState);
                if (effectiveState != ArrivalState.EN_ROUTE) {
                    publishNotification(booking, stop, effectiveState);
                }
            }

            resolved.put(booking.getBookingId(), effectiveState);
        }

        return resolved;
    }

    private ArrivalState classify(double distanceToTargetMeters) {
        if (distanceToTargetMeters <= properties.getArrivedRadiusMeters()) {
            return ArrivalState.ARRIVED;
        }
        if (distanceToTargetMeters <= properties.getApproachingRadiusMeters()) {
            return ArrivalState.APPROACHING;
        }
        return ArrivalState.EN_ROUTE;
    }

    private ArrivalState maxOf(ArrivalState a, ArrivalState b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    private void publishNotification(Booking booking, StopType stop, ArrivalState state) {
        boolean arrived = state == ArrivalState.ARRIVED;
        String type = notificationType(stop, arrived);
        String title = arrived ? "Driver arrived" : "Driver approaching";
        String place = stop == StopType.PICKUP ? "pickup" : "drop-off";
        String body = arrived
                ? "Your driver has arrived at " + place + "."
                : "Your driver is approaching " + place + ".";

        eventPublisher.publishEvent(new NotificationEvent(
                booking.getPassenger().getId(), NotificationCategory.RIDE, type, title, body,
                "BOOKING", booking.getBookingId()));
    }

    private String notificationType(StopType stop, boolean arrived) {
        return switch (stop) {
            case PICKUP -> arrived ? "RIDE_DRIVER_ARRIVED_PICKUP" : "RIDE_DRIVER_APPROACHING_PICKUP";
            case DROPOFF -> arrived ? "RIDE_DRIVER_ARRIVED_DROPOFF" : "RIDE_DRIVER_APPROACHING_DROPOFF";
        };
    }
}
