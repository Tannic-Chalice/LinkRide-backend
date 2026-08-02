package com.linkride.backend.notification;

/** Platform a {@link DeviceToken} belongs to (Phase 6). Only ANDROID has a real client today;
 * FCM already unifies APNs/web push behind the same send API, so this costs nothing to include now. */
public enum DevicePlatform {
    ANDROID,
    IOS,
    WEB
}
