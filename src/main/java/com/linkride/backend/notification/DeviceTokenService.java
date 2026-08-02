package com.linkride.backend.notification;

import java.util.List;
import java.util.UUID;

public interface DeviceTokenService {

    /** Upserts by {@code fcmToken} — a device holds exactly one current token at a time (§9). */
    DeviceTokenResponse registerToken(UUID userId, String fcmToken, DevicePlatform platform);

    /** User-initiated (e.g. logout) — only the token's own owner may deactivate it. */
    void deactivateToken(UUID userId, String fcmToken);

    /** System-triggered, from the FCM error-feedback loop (an {@code UNREGISTERED} response) — no ownership check. */
    void deactivateByToken(String fcmToken);

    List<DeviceToken> listActiveTokensForUser(UUID userId);
}
