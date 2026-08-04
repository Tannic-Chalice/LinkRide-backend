package com.linkride.backend.tracking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

/**
 * Safety-net eviction for {@link LiveRideState} that never reaches an explicit terminal call —
 * a crashed driver app, a force-quit, anything that means {@code RideService.completeRide}/
 * {@code cancelRide}'s own cleanup hook never runs (§14 of
 * backend/docs/phase-7-live-trip-management.md). Those two hooks handle every ordinary path;
 * this only catches what they can't.
 *
 * <p>The first {@code @Scheduled} task in this codebase — {@code AsyncConfig} enables
 * {@code @Scheduled} for it, the same "first of its kind" footnote {@code AsyncConfig} itself
 * carries for {@code @Async}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleLiveStateReaper {

    private final LiveRideStateStore liveRideStateStore;
    private final TrackingProperties properties;

    @Scheduled(fixedDelayString = "${linkride.tracking.reap-interval-millis:900000}")
    public void reap() {
        Collection<UUID> staleRideIds = liveRideStateStore.staleRideIds(properties.getStaleIdleThreshold());
        for (UUID rideId : staleRideIds) {
            liveRideStateStore.remove(rideId);
        }
        if (!staleRideIds.isEmpty()) {
            log.info("Evicted {} stale LiveRideState entr{} (idle past {})",
                    staleRideIds.size(), staleRideIds.size() == 1 ? "y" : "ies", properties.getStaleIdleThreshold());
        }
    }
}
