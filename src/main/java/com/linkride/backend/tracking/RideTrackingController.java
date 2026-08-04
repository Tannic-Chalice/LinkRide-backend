package com.linkride.backend.tracking;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Pull-based fallback for live tracking (§12) — the pre-connect initial load and the offline/
 * polling fallback, matching Phase 6's own REST-pull-as-baseline precedent.
 *
 * <p>Security: all routes require a valid Supabase JWT (enforced globally by
 * {@link com.linkride.backend.config.SecurityConfig}). The caller's UUID is always resolved from
 * the JWT — never from the request body.</p>
 */
@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
public class RideTrackingController {

    private final LocationUpdateService locationUpdateService;

    @GetMapping("/{rideId}/tracking")
    public ResponseEntity<RideProgressSnapshot> getTracking(@PathVariable UUID rideId, Authentication authentication) {
        UUID callerId = UUID.fromString(authentication.getName());
        RideProgressSnapshot snapshot = locationUpdateService.getSnapshot(callerId, rideId);
        return ResponseEntity.ok(snapshot);
    }
}
