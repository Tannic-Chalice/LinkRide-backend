package com.linkride.backend.notification;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class DeviceTokenResponse {

    private UUID deviceTokenId;
    private String fcmToken;
    private DevicePlatform platform;
    private Boolean active;
    private OffsetDateTime lastSeenAt;

    public static DeviceTokenResponse from(DeviceToken token) {
        return DeviceTokenResponse.builder()
                .deviceTokenId(token.getDeviceTokenId())
                .fcmToken(token.getFcmToken())
                .platform(token.getPlatform())
                .active(token.getActive())
                .lastSeenAt(token.getLastSeenAt())
                .build();
    }
}
