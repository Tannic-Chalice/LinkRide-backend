package com.linkride.backend.tracking;

import com.linkride.backend.config.AsyncConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

/**
 * A real, minimal Spring context (not a plain Mockito unit test) — proving {@code @Async}
 * dispatch (§11.3/ADR-9) is a property of Spring's proxy plus {@code AsyncConfig}'s executor,
 * which only a live container can demonstrate. Deliberately scoped to just
 * {@link AsyncConfig} + {@link LiveTrackingBroadcaster} + a mocked {@link SimpMessagingTemplate}
 * — no full {@code @SpringBootTest}, so no JWKS/network dependency from {@code JwtService}.
 */
@SpringJUnitConfig
class LiveTrackingBroadcasterTest {

    @Configuration
    @Import({AsyncConfig.class, LiveTrackingBroadcaster.class})
    static class TestConfig {
        @Bean
        SimpMessagingTemplate messagingTemplate() {
            return Mockito.mock(SimpMessagingTemplate.class);
        }
    }

    @Autowired
    private LiveTrackingBroadcaster broadcaster;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void pushToUser_dispatchesToConvertAndSendToUserOnTheRideProgressQueue() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        Object payload = new Object();
        CountDownLatch delivered = new CountDownLatch(1);
        doAnswer(invocation -> {
            delivered.countDown();
            return null;
        }).when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any(Object.class));

        broadcaster.pushToUser(userId, payload);

        assertThat(delivered.await(2, TimeUnit.SECONDS)).isTrue();
        verify(messagingTemplate).convertAndSendToUser(userId.toString(), "/queue/ride-progress", payload);
    }

    @Test
    void pushToUser_returnsWithoutWaitingForTheBrokerCallToComplete() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        CountDownLatch releaseBrokerCall = new CountDownLatch(1);
        doAnswer(invocation -> {
            // Simulates a stalled/slow broker delivery -- this must never be on the caller's thread.
            assertThat(releaseBrokerCall.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any(Object.class));

        long startNanos = System.nanoTime();
        broadcaster.pushToUser(userId, new Object());
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        assertThat(elapsedMillis)
                .as("pushToUser must return immediately -- the broker call is still blocked on the latch")
                .isLessThan(500);

        releaseBrokerCall.countDown();
    }
}
