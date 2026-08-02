package com.linkride.backend.tracking;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the ordered §7.1 pipeline: timestamp sanity, duplicate/out-of-order,
 * impossible jump — coordinate bounds are covered separately by
 * {@link LocationUpdateRequestTest} since they're Bean Validation, not this class.
 */
class GpsFixValidatorTest {

    private final TrackingProperties properties = new TrackingProperties();
    private final GpsFixValidator validator = new GpsFixValidator(properties);

    @Test
    void firstEverPing_noPreviousState_isAccepted() {
        Instant now = Instant.now();
        LocationUpdateRequest incoming = request(12.9716, 77.5946, now);

        assertThat(validator.validate(Optional.empty(), incoming, now)).isEqualTo(GpsValidationOutcome.ACCEPTED);
    }

    @Test
    void timestampWithinFutureSkew_isAccepted() {
        Instant now = Instant.now();
        Instant recordedAt = now.plusSeconds(properties.getMaxFutureSkewSeconds());
        LocationUpdateRequest incoming = request(12.9716, 77.5946, recordedAt);

        assertThat(validator.validate(Optional.empty(), incoming, now)).isEqualTo(GpsValidationOutcome.ACCEPTED);
    }

    @Test
    void timestampBeyondFutureSkew_isRejected() {
        Instant now = Instant.now();
        Instant recordedAt = now.plusSeconds(properties.getMaxFutureSkewSeconds() + 1);
        LocationUpdateRequest incoming = request(12.9716, 77.5946, recordedAt);

        assertThat(validator.validate(Optional.empty(), incoming, now))
                .isEqualTo(GpsValidationOutcome.REJECTED_FUTURE_TIMESTAMP);
    }

    @Test
    void timestampWithinMaxAge_isAccepted() {
        Instant now = Instant.now();
        Instant recordedAt = now.minusSeconds(properties.getMaxFixAgeSeconds());
        LocationUpdateRequest incoming = request(12.9716, 77.5946, recordedAt);

        assertThat(validator.validate(Optional.empty(), incoming, now)).isEqualTo(GpsValidationOutcome.ACCEPTED);
    }

    @Test
    void timestampBeyondMaxAge_isRejected() {
        Instant now = Instant.now();
        Instant recordedAt = now.minusSeconds(properties.getMaxFixAgeSeconds() + 1);
        LocationUpdateRequest incoming = request(12.9716, 77.5946, recordedAt);

        assertThat(validator.validate(Optional.empty(), incoming, now))
                .isEqualTo(GpsValidationOutcome.REJECTED_STALE_TIMESTAMP);
    }

    @Test
    void recordedAtNotAfterLastUpdatedAt_isIgnoredAsDuplicate() {
        Instant now = Instant.now();
        LiveRideState previous = liveState(now.minusSeconds(10), 12.9716, 77.5946);
        LocationUpdateRequest incoming = request(12.98, 77.60, now.minusSeconds(10));

        assertThat(validator.validate(Optional.of(previous), incoming, now))
                .isEqualTo(GpsValidationOutcome.IGNORED_DUPLICATE);
    }

    @Test
    void olderTimestampThanLastUpdatedAt_isIgnoredAsDuplicate() {
        Instant now = Instant.now();
        LiveRideState previous = liveState(now.minusSeconds(5), 12.9716, 77.5946);
        LocationUpdateRequest incoming = request(12.98, 77.60, now.minusSeconds(10));

        assertThat(validator.validate(Optional.of(previous), incoming, now))
                .isEqualTo(GpsValidationOutcome.IGNORED_DUPLICATE);
    }

    @Test
    void sameCoordinatesAsLastFix_isIgnoredAsDuplicateEvenWithNewerTimestamp() {
        Instant now = Instant.now();
        LiveRideState previous = liveState(now.minusSeconds(10), 12.9716, 77.5946);
        LocationUpdateRequest incoming = request(12.9716, 77.5946, now);

        assertThat(validator.validate(Optional.of(previous), incoming, now))
                .isEqualTo(GpsValidationOutcome.IGNORED_DUPLICATE);
    }

    @Test
    void plausibleMovement_isAccepted() {
        Instant now = Instant.now();
        LiveRideState previous = liveState(now.minusSeconds(10), 12.9716, 77.5946);
        // ~100m in 10s => 10 m/s, well under the default ~55 m/s ceiling.
        LocationUpdateRequest incoming = request(12.9725, 77.5946, now);

        assertThat(validator.validate(Optional.of(previous), incoming, now)).isEqualTo(GpsValidationOutcome.ACCEPTED);
    }

    @Test
    void impliedSpeedBeyondMaxPlausible_isRejectedAsImpossibleJump() {
        Instant now = Instant.now();
        LiveRideState previous = liveState(now.minusSeconds(1), 12.9716, 77.5946);
        // ~1 degree of latitude (~111km) covered in 1 second is nowhere near plausible.
        LocationUpdateRequest incoming = request(13.9716, 77.5946, now);

        assertThat(validator.validate(Optional.of(previous), incoming, now))
                .isEqualTo(GpsValidationOutcome.REJECTED_IMPOSSIBLE_JUMP);
    }

    private LocationUpdateRequest request(double latitude, double longitude, Instant recordedAt) {
        LocationUpdateRequest request = new LocationUpdateRequest();
        request.setLatitude(latitude);
        request.setLongitude(longitude);
        request.setRecordedAt(recordedAt);
        return request;
    }

    private LiveRideState liveState(Instant lastUpdatedAt, double lat, double lng) {
        return new LiveRideState(UUID.randomUUID(), lat, lng, lastUpdatedAt, new java.util.HashMap<>());
    }
}
