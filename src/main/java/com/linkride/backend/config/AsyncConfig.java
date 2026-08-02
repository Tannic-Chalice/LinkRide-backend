package com.linkride.backend.config;

import com.linkride.backend.filter.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Enables {@code @Async} processing for Phase 6 Notifications (the first {@code @Async} use in
 * this codebase — see backend/docs/phase-6-notifications.md §7). The executor's {@link
 * MdcPropagatingTaskDecorator} carries {@link CorrelationIdFilter}'s correlation ID onto the
 * async thread, exactly the propagation Phase 5 §3 flagged as "relevant the moment Notifications
 * lands."
 *
 * <p>Also enables {@code @Scheduled} for {@code StaleLiveStateReaper} (Phase 7 §14 — see
 * backend/docs/phase-7-live-trip-management.md) — the first scheduled task in this codebase,
 * grouped here rather than a dedicated config class for the same "one small annotation, no new
 * file" reasoning {@code @EnableAsync} already got.</p>
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean("notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notif-async-");
        executor.setTaskDecorator(new MdcPropagatingTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * Backs {@code LiveTrackingBroadcaster.pushToUser} (Phase 7 §11.3/ADR-9 — see
     * backend/docs/phase-7-live-trip-management.md) — a slow or unreachable passenger connection
     * must never delay or fail the driver's own {@code POST /location} response, the same
     * "additive, never disruptive" posture {@code notificationExecutor} already holds for
     * notification delivery. Separate pool from {@code notificationExecutor} so a backlog in one
     * delivery path can't starve the other.
     */
    @Bean("trackingBroadcastExecutor")
    public Executor trackingBroadcastExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("tracking-broadcast-");
        executor.setTaskDecorator(new MdcPropagatingTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * Copies the submitting (request) thread's MDC context map onto the pool thread that actually
     * runs the task, and clears it afterward — pool threads are reused across many async tasks,
     * so leaving stale MDC behind would leak one request's correlation ID onto another's log
     * lines, the same hazard {@link CorrelationIdFilter} itself guards against for Tomcat's
     * worker threads.
     */
    static class MdcPropagatingTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            return () -> {
                try {
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        }
    }
}
