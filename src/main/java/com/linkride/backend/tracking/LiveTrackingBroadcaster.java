package com.linkride.backend.tracking;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The only class in this codebase allowed to touch a live socket (§4 of
 * backend/docs/phase-7-live-trip-management.md). Wraps
 * {@link SimpMessagingTemplate#convertAndSendToUser} — unicast only, no topics (§11.1/ADR-1).
 *
 * <p>Dispatch runs on {@code trackingBroadcastExecutor} ({@link
 * com.linkride.backend.config.AsyncConfig}), off the thread handling the driver's
 * {@code POST /location}, so a slow or unreachable passenger connection never delays or fails
 * that request (§11.3/ADR-9) — the same {@code @Async}/{@code MdcPropagatingTaskDecorator}
 * pattern already established for Phase 6 notification delivery.</p>
 */
@Component
@RequiredArgsConstructor
public class LiveTrackingBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    @Async("trackingBroadcastExecutor")
    public void pushToUser(UUID userId, Object payload) {
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/ride-progress", payload);
    }
}
