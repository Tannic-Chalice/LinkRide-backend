package com.linkride.backend.tracking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static com.linkride.backend.route.geometry.GeoMath.haversineMeters;

/**
 * Ordered, independently-testable GPS validation pipeline (§7). Runs before
 * {@code RouteProgressEngine}, {@code EtaEngine}, {@code ArrivalDetectionService}, or
 * {@link LiveRideStateStore} ever see a fix. Coordinate bounds (§7.1 step 1) are checked
 * separately, by Bean Validation on {@link LocationUpdateRequest} itself — everything checked
 * here needs the ride's previous {@link LiveRideState} and the current server clock, neither of
 * which Bean Validation can see.
 */
@Component
@RequiredArgsConstructor
public class GpsFixValidator {

    private final TrackingProperties properties;

    public GpsValidationOutcome validate(Optional<LiveRideState> previous, LocationUpdateRequest incoming, Instant now) {
        Duration age = Duration.between(incoming.getRecordedAt(), now);

        if (age.isNegative() && age.negated().compareTo(Duration.ofSeconds(properties.getMaxFutureSkewSeconds())) > 0) {
            return GpsValidationOutcome.REJECTED_FUTURE_TIMESTAMP;
        }
        if (!age.isNegative() && age.compareTo(Duration.ofSeconds(properties.getMaxFixAgeSeconds())) > 0) {
            return GpsValidationOutcome.REJECTED_STALE_TIMESTAMP;
        }

        if (previous.isEmpty()) {
            return GpsValidationOutcome.ACCEPTED;
        }
        LiveRideState last = previous.get();

        boolean notStrictlyAfter = !incoming.getRecordedAt().isAfter(last.getLastUpdatedAt());
        boolean sameCoordinates = incoming.getLatitude() == last.getLastLatitude()
                && incoming.getLongitude() == last.getLastLongitude();
        if (notStrictlyAfter || sameCoordinates) {
            return GpsValidationOutcome.IGNORED_DUPLICATE;
        }

        double distanceMeters = haversineMeters(
                last.getLastLatitude(), last.getLastLongitude(),
                incoming.getLatitude(), incoming.getLongitude());
        double secondsElapsed = Duration.between(last.getLastUpdatedAt(), incoming.getRecordedAt()).toMillis() / 1000.0;
        double impliedSpeedMps = distanceMeters / secondsElapsed;

        if (impliedSpeedMps > properties.getMaxPlausibleSpeedMps()) {
            return GpsValidationOutcome.REJECTED_IMPOSSIBLE_JUMP;
        }

        return GpsValidationOutcome.ACCEPTED;
    }
}
