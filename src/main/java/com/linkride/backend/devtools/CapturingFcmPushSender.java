package com.linkride.backend.devtools;

import com.linkride.backend.notification.FcmPushSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Devtools-only {@link FcmPushSender}: never calls real Firebase, regardless of {@code
 * linkride.notifications.push-provider} — a devtools-driven seeder/scenario run must never
 * depend on (or accidentally use) real push credentials. Captures each send in memory instead, so
 * {@link DemoScenarioService}/tests can assert on what would have been pushed, the same way
 * {@link CapturingOtpNotificationService} already stands in for a real OTP provider.
 *
 * <p>{@code @Primary} so it wins over whichever real {@code FcmPushSender} implementation the
 * {@code push-provider} property would otherwise select — only registered at all when devtools is
 * enabled, so production/plain-dev behavior is unaffected.</p>
 */
@Slf4j
@Service
@Primary
@ConditionalOnProperty(prefix = "linkride.devtools", name = "enabled", havingValue = "true")
public class CapturingFcmPushSender implements FcmPushSender {

    private final Map<String, CapturedPush> lastPushByToken = new ConcurrentHashMap<>();

    @Override
    public PushOutcome send(String fcmToken, String title, String body, Map<String, String> data) {
        log.info("[DEV ONLY, no real delivery] Push to token {}: {} — {} ({})", fcmToken, title, body, data);
        lastPushByToken.put(fcmToken, new CapturedPush(title, body, data));
        return PushOutcome.SENT;
    }

    /** The most recent push "sent" to this token, for a scenario/test to assert on. */
    public CapturedPush getLastPush(String fcmToken) {
        CapturedPush push = lastPushByToken.get(fcmToken);
        if (push == null) {
            throw new IllegalStateException("No push was captured for token " + fcmToken);
        }
        return push;
    }

    public record CapturedPush(String title, String body, Map<String, String> data) {
    }
}
