package com.linkride.backend.tracking;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryLiveRideStateStoreTest {

    private final InMemoryLiveRideStateStore store = new InMemoryLiveRideStateStore();

    @Test
    void get_unknownRide_returnsEmpty() {
        assertThat(store.get(UUID.randomUUID())).isEmpty();
    }

    @Test
    void save_thenGet_returnsTheSavedState() {
        UUID rideId = UUID.randomUUID();
        LiveRideState state = state(rideId, Instant.now());

        store.save(rideId, state);

        Optional<LiveRideState> reloaded = store.get(rideId);
        assertThat(reloaded).contains(state);
    }

    @Test
    void save_calledAgainForSameRide_overwritesThePreviousState() {
        UUID rideId = UUID.randomUUID();
        store.save(rideId, state(rideId, Instant.now().minusSeconds(30)));

        LiveRideState latest = state(rideId, Instant.now());
        store.save(rideId, latest);

        assertThat(store.get(rideId)).contains(latest);
    }

    @Test
    void remove_deletesTheState() {
        UUID rideId = UUID.randomUUID();
        store.save(rideId, state(rideId, Instant.now()));

        store.remove(rideId);

        assertThat(store.get(rideId)).isEmpty();
    }

    @Test
    void remove_unknownRide_isANoOp() {
        assertThat(store.get(UUID.randomUUID())).isEmpty();
        store.remove(UUID.randomUUID());
    }

    @Test
    void staleRideIds_returnsOnlyRidesOlderThanTheThreshold() {
        UUID staleRide = UUID.randomUUID();
        UUID freshRide = UUID.randomUUID();
        store.save(staleRide, state(staleRide, Instant.now().minus(Duration.ofHours(5))));
        store.save(freshRide, state(freshRide, Instant.now()));

        Collection<UUID> stale = store.staleRideIds(Duration.ofHours(4));

        assertThat(stale).containsExactly(staleRide);
    }

    @Test
    void staleRideIds_noRidesPastThreshold_returnsEmpty() {
        UUID rideId = UUID.randomUUID();
        store.save(rideId, state(rideId, Instant.now()));

        assertThat(store.staleRideIds(Duration.ofHours(4))).isEmpty();
    }

    private LiveRideState state(UUID rideId, Instant lastUpdatedAt) {
        return new LiveRideState(rideId, 12.9716, 77.5946, lastUpdatedAt, new java.util.HashMap<>());
    }
}
