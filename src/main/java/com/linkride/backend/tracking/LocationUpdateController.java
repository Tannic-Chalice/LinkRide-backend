package com.linkride.backend.tracking;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Driver GPS ingestion (§12 of backend/docs/phase-7-live-trip-management.md).
 *
 * <p>Security: all routes require a valid Supabase JWT (enforced globally by
 * {@link com.linkride.backend.config.SecurityConfig}). The driver's UUID is always resolved from
 * the JWT — never from the request body, same convention as {@link com.linkride.backend.ride.RideController}.</p>
 */
@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
public class LocationUpdateController {

    private final LocationUpdateService locationUpdateService;

    @PostMapping("/{rideId}/location")
    public ResponseEntity<RideProgressSnapshot> updateLocation(
            @PathVariable UUID rideId,
            @Valid @RequestBody LocationUpdateRequest request,
            Authentication authentication) {

        UUID driverId = UUID.fromString(authentication.getName());
        RideProgressSnapshot snapshot = locationUpdateService.updateLocation(driverId, rideId, request);
        return ResponseEntity.ok(snapshot);
    }
}
