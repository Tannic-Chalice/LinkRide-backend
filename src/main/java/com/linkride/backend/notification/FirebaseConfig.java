package com.linkride.backend.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Initializes the Firebase Admin SDK from a service account credential file (Phase 6 — see
 * backend/docs/phase-6-notifications.md §11). Guarded by the same conditions as {@link
 * FirebaseFcmPushSender} so the credential file is never even opened under {@code
 * linkride.notifications.push-provider=logging} (local dev without a Firebase project), and never
 * opened when devtools is enabled either — {@code
 * com.linkride.backend.devtools.CapturingFcmPushSender} is {@code @Primary} whenever devtools is
 * on, so this bean would otherwise exist only to sit unused, and a bad/missing credential file in
 * a devtools/test environment would needlessly fail application startup for a bean nothing uses.
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "linkride.notifications", name = "push-provider", havingValue = "firebase", matchIfMissing = true)
@ConditionalOnProperty(prefix = "linkride.devtools", name = "enabled", havingValue = "false", matchIfMissing = true)
public class FirebaseConfig {

    private final NotificationProperties notificationProperties;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getApps().get(0);
        }

        try (FileInputStream credentialStream = new FileInputStream(notificationProperties.getFcm().getCredentialsPath())) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialStream))
                    .build();
            return FirebaseApp.initializeApp(options);
        }
    }
}
