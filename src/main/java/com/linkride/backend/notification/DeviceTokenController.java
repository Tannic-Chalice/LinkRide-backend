package com.linkride.backend.notification;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * FCM device-token registration for the authenticated user (Phase 6 — see
 * backend/docs/phase-6-notifications.md §9/§12).
 */
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    @PostMapping
    public ResponseEntity<DeviceTokenResponse> register(
            @Valid @RequestBody DeviceTokenRegisterRequest request, Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        DeviceTokenResponse response = deviceTokenService.registerToken(userId, request.getFcmToken(), request.getPlatform());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{fcmToken}")
    public ResponseEntity<Void> deactivate(@PathVariable String fcmToken, Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        deviceTokenService.deactivateToken(userId, fcmToken);
        return ResponseEntity.noContent().build();
    }
}
