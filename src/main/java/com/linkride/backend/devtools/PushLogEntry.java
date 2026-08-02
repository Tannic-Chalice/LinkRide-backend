package com.linkride.backend.devtools;

import lombok.Builder;
import lombok.Data;

/**
 * Devtools-only view of a recent notification, for {@code GET /api/v1/devtools/push-log} — lets a
 * demo/test flow (POST booking, accept it, GET this endpoint) see what would have been pushed
 * without a mobile client or real FCM credentials. Deliberately reads straight from {@code
 * NotificationRepository} rather than capturing {@code FcmPushSender} sends: a demo run typically
 * has no {@code DeviceToken} registered for anyone, so every notification's {@code deliveryStatus}
 * is {@code SKIPPED} and nothing would ever reach the push sender to capture — the persisted
 * notification row is still the fact worth showing, per this codebase's own "backend is the
 * single source of truth" rule (backend/docs/phase-6-notifications.md §4/ADR-3).
 */
@Data
@Builder
public class PushLogEntry {
    private String recipient;
    private String type;
    private String title;
    private String body;
}
