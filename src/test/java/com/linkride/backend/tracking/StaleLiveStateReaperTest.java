package com.linkride.backend.tracking;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §15 — inject a fake clock and assert eviction only past the configured threshold. Uses a real
 * {@link InMemoryLiveRideStateStore} with a {@link Clock#fixed} clock rather than mocking
 * {@link LiveRideStateStore}, so the actual boundary math under test is the same code path
 * production traffic hits.
 */
class StaleLiveStateReaperTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T12:00:00Z");

    private InMemoryLiveRideStateStore store(Instant fixedNow) {
        return new InMemoryLiveRideStateStore(Clock.fixed(fixedNow, ZoneOffset.UTC));
    }

    private LiveRideState stateAt(Instant lastUpdatedAt) {
        return new LiveRideState(UUID.randomUUID(), 12.9716, 77.5946, lastUpdatedAt, new HashMap<>());
    }

    @Test
    void reap_evictsOnlyRidesIdlePastTheConfiguredThreshold() {
        InMemoryLiveRideStateStore store = store(FIXED_NOW);
        TrackingProperties properties = new TrackingProperties();
        properties.setStaleIdleThreshold(Duration.ofHours(4));

        UUID staleRide = UUID.randomUUID();
        UUID freshRide = UUID.randomUUID();
        store.save(staleRide, stateAt(FIXED_NOW.minus(Duration.ofHours(5))));
        store.save(freshRide, stateAt(FIXED_NOW.minus(Duration.ofHours(1))));

        new StaleLiveStateReaper(store, properties).reap();

        assertThat(store.get(staleRide)).isEmpty();
        assertThat(store.get(freshRide)).isPresent();
    }

    @Test
    void reap_rideExactlyAtTheThreshold_isNotEvicted() {
        InMemoryLiveRideStateStore store = store(FIXED_NOW);
        TrackingProperties properties = new TrackingProperties();
        properties.setStaleIdleThreshold(Duration.ofHours(4));

        UUID rideId = UUID.randomUUID();
        store.save(rideId, stateAt(FIXED_NOW.minus(Duration.ofHours(4))));

        new StaleLiveStateReaper(store, properties).reap();

        assertThat(store.get(rideId)).isPresent();
    }

    @Test
    void reap_noStaleRides_isANoOp() {
        InMemoryLiveRideStateStore store = store(FIXED_NOW);
        TrackingProperties properties = new TrackingProperties();
        UUID rideId = UUID.randomUUID();
        store.save(rideId, stateAt(FIXED_NOW));

        new StaleLiveStateReaper(store, properties).reap();

        assertThat(store.get(rideId)).isPresent();
    }
}
