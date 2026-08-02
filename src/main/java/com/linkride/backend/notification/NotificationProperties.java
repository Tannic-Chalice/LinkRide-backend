package com.linkride.backend.notification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Tunable constants for Phase 6 Notifications, following the same {@code @ConfigurationProperties}
 * pattern as {@link com.linkride.backend.boarding.BoardingProperties}.
 */
@Data
@Component
@ConfigurationProperties(prefix = "linkride.notifications")
public class NotificationProperties {

    private int inboxDefaultPageSize = 20;
    private int inboxMaxPageSize = 100;

    private final Fcm fcm = new Fcm();

    @Data
    public static class Fcm {
        /** Path to the Firebase Admin SDK service account credential file. */
        private String credentialsPath = "./serviceAccountKey.json";

        /** Retries only cover transient FCM errors, same shape as {@code linkride.google-maps.max-retries}. */
        private int maxRetries = 2;
        private Duration retryBackoff = Duration.ofMillis(200);
    }
}
