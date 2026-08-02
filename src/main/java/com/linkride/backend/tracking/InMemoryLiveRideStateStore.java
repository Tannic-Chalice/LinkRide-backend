package com.linkride.backend.tracking;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * V1 {@link LiveRideStateStore} adapter — a single {@link ConcurrentHashMap}, per §6.2. There is
 * exactly one driver per ride, but a retried/duplicate HTTP request is a real case to guard
 * against, so every write replaces the mapping for a {@code rideId} in one atomic
 * {@code ConcurrentHashMap} operation rather than a separate read-then-write.
 */
@Service
public class InMemoryLiveRideStateStore implements LiveRideStateStore {

    private final ConcurrentHashMap<UUID, LiveRideState> states = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryLiveRideStateStore() {
        this(Clock.systemUTC());
    }

    /** Package-visible so {@code StaleLiveStateReaper}'s own test can inject a fake clock (§15). */
    InMemoryLiveRideStateStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<LiveRideState> get(UUID rideId) {
        return Optional.ofNullable(states.get(rideId));
    }

    @Override
    public void save(UUID rideId, LiveRideState state) {
        states.put(rideId, state);
    }

    @Override
    public void remove(UUID rideId) {
        states.remove(rideId);
    }

    @Override
    public Collection<UUID> staleRideIds(Duration idleThreshold) {
        Instant cutoff = clock.instant().minus(idleThreshold);
        return states.entrySet().stream()
                .filter(entry -> entry.getValue().getLastUpdatedAt().isBefore(cutoff))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
