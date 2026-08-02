package com.linkride.backend.tracking;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code InMemoryLiveRideStateStore} has no database and no locking of its own to prove —
 * unlike {@code BoardingConcurrencyTest}/{@code BookingConcurrencyTest}, which exist to prove a
 * JPA {@code @Version} column really does serialize concurrent transactions. What this proves
 * instead: a burst of simultaneous, retried/duplicate GPS pings for the same ride (a real case,
 * per §6.2) never corrupts the map — {@code get} always sees exactly one of the saved states,
 * never a torn read, a lost update, or a concurrent-modification failure.
 */
class InMemoryLiveRideStateStoreConcurrencyTest {

    private final InMemoryLiveRideStateStore store = new InMemoryLiveRideStateStore();

    @Test
    void save_concurrentPingsForSameRide_lastWriteWinsWithNoCorruption() throws Exception {
        UUID rideId = UUID.randomUUID();
        int contenders = 16;
        List<LiveRideState> candidates = IntStream.range(0, contenders)
                .mapToObj(i -> new LiveRideState(
                        rideId, 12.97 + i * 0.0001, 77.59 + i * 0.0001,
                        Instant.now().plusMillis(i), new java.util.HashMap<>()))
                .toList();

        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<Void>> futures = candidates.stream()
                .map(candidate -> pool.submit((Callable<Void>) () -> {
                    ready.countDown();
                    go.await();
                    store.save(rideId, candidate);
                    return null;
                }))
                .collect(Collectors.toList());

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();

        for (Future<Void> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        Optional<LiveRideState> result = store.get(rideId);
        assertThat(result).isPresent();
        assertThat(candidates)
                .as("the surviving state must be exactly one of the concurrently-saved candidates, never a mix")
                .contains(result.get());
    }

    @Test
    void save_concurrentPingsForDifferentRides_allSurviveIndependently() throws Exception {
        int rideCount = 20;
        List<UUID> rideIds = IntStream.range(0, rideCount).mapToObj(i -> UUID.randomUUID()).toList();

        ExecutorService pool = Executors.newFixedThreadPool(rideCount);
        CountDownLatch ready = new CountDownLatch(rideCount);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<Void>> futures = rideIds.stream()
                .map(rideId -> pool.submit((Callable<Void>) () -> {
                    ready.countDown();
                    go.await();
                    store.save(rideId, new LiveRideState(
                            rideId, 12.97, 77.59, Instant.now(), new java.util.HashMap<>()));
                    return null;
                }))
                .collect(Collectors.toList());

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();

        for (Future<Void> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        for (UUID rideId : rideIds) {
            assertThat(store.get(rideId)).isPresent();
        }
    }

    @Test
    void staleRideIds_duringConcurrentSaves_neverThrows() throws Exception {
        int rideCount = 50;
        List<UUID> rideIds = IntStream.range(0, rideCount).mapToObj(i -> UUID.randomUUID()).toList();
        ExecutorService writers = Executors.newFixedThreadPool(4);

        List<Future<Void>> writes = rideIds.stream()
                .map(rideId -> writers.submit((Callable<Void>) () -> {
                    for (int i = 0; i < 20; i++) {
                        store.save(rideId, new LiveRideState(
                                rideId, 12.97, 77.59, Instant.now(), new java.util.HashMap<>()));
                    }
                    return null;
                }))
                .collect(Collectors.toList());

        for (int i = 0; i < 20; i++) {
            Set<UUID> stale = Set.copyOf(store.staleRideIds(Duration.ofHours(4)));
            assertThat(stale).isEmpty();
        }

        for (Future<Void> write : writes) {
            write.get(30, TimeUnit.SECONDS);
        }
        writers.shutdown();
    }
}
