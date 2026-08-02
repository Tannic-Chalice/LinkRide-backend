package com.linkride.backend.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Development-only {@link FcmPushSender}: logs instead of calling Firebase. There's no
 * requirement to hold a real Firebase project for local dev/tests — activate with {@code
 * linkride.notifications.push-provider=logging}; {@link FirebaseFcmPushSender} is the default.
 */
@Slf4j
@Service("loggingFcmPushSender")
@ConditionalOnProperty(prefix = "linkride.notifications", name = "push-provider", havingValue = "logging")
public class LoggingFcmPushSender implements FcmPushSender {

    @Override
    public PushOutcome send(String fcmToken, String title, String body, Map<String, String> data) {
        log.info("[DEV ONLY, no real delivery] Push to token {}: {} — {} ({})", fcmToken, title, body, data);
        return PushOutcome.SENT;
    }
}
