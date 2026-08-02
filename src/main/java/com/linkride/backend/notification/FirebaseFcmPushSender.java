package com.linkride.backend.notification;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Production {@link FcmPushSender} backed by the Firebase Admin SDK. Retry only covers transient
 * delivery failures — same reasoning as {@link com.linkride.backend.route.google.GoogleRouteProvider}:
 * a definitive rejection (e.g. an invalid/unregistered token) is not worth retrying with identical
 * input. Default active {@link FcmPushSender}; switch to the dev-only stand-in via {@code
 * linkride.notifications.push-provider=logging}.
 */
@Slf4j
@Service("firebaseFcmPushSender")
@ConditionalOnProperty(prefix = "linkride.notifications", name = "push-provider", havingValue = "firebase", matchIfMissing = true)
@ConditionalOnProperty(prefix = "linkride.devtools", name = "enabled", havingValue = "false", matchIfMissing = true)
public class FirebaseFcmPushSender implements FcmPushSender {

    private final FirebaseApp firebaseApp;
    private final NotificationProperties.Fcm properties;

    public FirebaseFcmPushSender(FirebaseApp firebaseApp, NotificationProperties notificationProperties) {
        this.firebaseApp = firebaseApp;
        this.properties = notificationProperties.getFcm();
    }

    @Override
    public PushOutcome send(String fcmToken, String title, String body, Map<String, String> data) {
        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data)
                .build();

        int totalAttempts = properties.getMaxRetries() + 1;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                FirebaseMessaging.getInstance(firebaseApp).send(message);
                return PushOutcome.SENT;
            } catch (FirebaseMessagingException e) {
                if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                    return PushOutcome.TOKEN_INVALID;
                }

                log.warn("FCM send failed (attempt {}/{}): {}", attempt, totalAttempts, e.getMessage());
                if (attempt < totalAttempts) {
                    sleepBackoff();
                }
            }
        }

        return PushOutcome.TRANSIENT_FAILURE;
    }

    private void sleepBackoff() {
        try {
            Thread.sleep(properties.getRetryBackoff().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
