package com.linkride.backend.notification;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * A user's own in-app notification history and preferences (Phase 6 — see
 * backend/docs/phase-6-notifications.md §12). Security: all routes require a valid Supabase JWT
 * (enforced globally by {@link com.linkride.backend.config.SecurityConfig}); the caller's UUID is
 * always resolved from the JWT, never from the request, and every notification/preference lookup
 * is scoped to that caller only.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<NotificationPageResponse> list(
            @RequestParam(required = false) NotificationCategory category,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(notificationService.listNotifications(userId, category, status, page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount(
            @RequestParam(required = false) NotificationCategory category,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(notificationService.getUnreadCount(userId, category));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable("id") UUID notificationId, Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(notificationService.markRead(userId, notificationId));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(
            @RequestParam(required = false) NotificationCategory category, Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        notificationService.markAllRead(userId, category);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@PathVariable("id") UUID notificationId, Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        notificationService.archive(userId, notificationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preferences")
    public ResponseEntity<List<NotificationPreferenceDto>> getPreferences(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(notificationService.getPreferences(userId));
    }

    @PutMapping("/preferences")
    public ResponseEntity<List<NotificationPreferenceDto>> updatePreferences(
            @Valid @RequestBody List<NotificationPreferenceDto> updates, Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(notificationService.updatePreferences(userId, updates));
    }
}
