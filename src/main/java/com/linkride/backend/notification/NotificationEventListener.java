package com.linkride.backend.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The single consumer of every {@link NotificationEvent} (Phase 6 — see
 * backend/docs/phase-6-notifications.md §6/§7). {@code phase = AFTER_COMMIT}: if the publishing
 * transaction rolls back, this method never runs at all, so a fact that didn't actually happen
 * can never produce a notification. {@code @Async}: runs off the request thread, so a slow or
 * unreachable FCM never holds up the HTTP response that triggered it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationEvent(NotificationEvent event) {
        try {
            notificationService.createAndDeliver(event);
        } catch (Exception e) {
            // Defense in depth: NotificationServiceImpl.createAndDeliver already never throws by
            // design, but this boundary must hold even if that guarantee is ever accidentally
            // broken -- a notification-delivery bug must never surface as an uncaught exception
            // on an async thread.
            log.error("Unexpected failure handling notification event {}: {}", event, e.getMessage(), e);
        }
    }
}
