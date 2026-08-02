package com.linkride.backend.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeviceTokenRegisterRequest {

    @NotBlank
    private String fcmToken;

    @NotNull
    private DevicePlatform platform;
}
