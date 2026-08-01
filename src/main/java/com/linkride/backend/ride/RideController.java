package com.linkride.backend.ride;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Driver ride creation (Phase 1) plus ride lifecycle transitions (Phase 3.6). Booking actions
 * live in the {@code com.linkride.backend.booking} package instead.
 *
 * <p>Security: all routes require a valid Supabase JWT (enforced globally by
 * {@link com.linkride.backend.config.SecurityConfig}). The driver's UUID is always
 * resolved from the JWT — never from the request body.</p>
 */
@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
public class RideController {

    private final RideService rideService;

    @PostMapping
    public ResponseEntity<RideResponse> createRide(
            @Valid @RequestBody RideCreateRequest request,
            Authentication authentication) {

        UUID driverId = UUID.fromString(authentication.getName());
        RideResponse response = rideService.createRide(driverId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{rideId}/cancel")
    public ResponseEntity<RideResponse> cancelRide(@PathVariable UUID rideId, Authentication authentication) {
        UUID driverId = UUID.fromString(authentication.getName());
        RideResponse response = rideService.cancelRide(driverId, rideId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{rideId}/start")
    public ResponseEntity<RideResponse> startRide(@PathVariable UUID rideId, Authentication authentication) {
        UUID driverId = UUID.fromString(authentication.getName());
        RideResponse response = rideService.startRide(driverId, rideId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{rideId}/complete")
    public ResponseEntity<RideResponse> completeRide(@PathVariable UUID rideId, Authentication authentication) {
        UUID driverId = UUID.fromString(authentication.getName());
        RideResponse response = rideService.completeRide(driverId, rideId);
        return ResponseEntity.ok(response);
    }
}
