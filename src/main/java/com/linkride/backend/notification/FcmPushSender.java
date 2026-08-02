package com.linkride.backend.notification;

import java.util.Map;

/**
 * Delivers a push notification to a single device token (Phase 6). No real provider is required
 * to be wired in for local dev — {@link LoggingFcmPushSender} is a development-only stand-in, the
 * same way {@link com.linkride.backend.route.NoOpRouteProvider}/{@link
 * com.linkride.backend.boarding.LoggingOtpNotificationService} already split a real provider from
 * a local-dev one.
 */
public interface FcmPushSender {

    /**
     * Never throws — a delivery failure is reported via {@link PushOutcome}, not an exception,
     * since a failed push must never be allowed to propagate back into the triggering business
     * transaction (architecture doc §4/ADR-3). {@code data} carries the fields a client needs to
     * deep-link ({@code type}, {@code relatedEntityType}, {@code relatedEntityId}).
     */
    PushOutcome send(String fcmToken, String title, String body, Map<String, String> data);

    enum PushOutcome {
        SENT,
        /** The token is no longer valid (uninstalled app, revoked registration) — caller should deactivate it. */
        TOKEN_INVALID,
        /** Transient failure after retries exhausted — the token itself may still be good. */
        TRANSIENT_FAILURE
    }
}
