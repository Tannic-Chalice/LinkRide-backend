package com.linkride.backend.boarding;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Driver-side read of a ride's boarding progress (Phase 4.5) — what the driver's app polls every
 * few seconds while boarding is in progress. Ride-scoped rather than booking-scoped, so it lives
 * in its own controller rather than {@link BoardingController} (mapped under
 * {@code /api/bookings/{bookingId}/boarding}, with no {@code rideId} path variable to hang this
 * off of).
 *
 * <p>Security: requires a valid Supabase JWT (enforced globally by
 * {@link com.linkride.backend.config.SecurityConfig}). The caller's UUID is always resolved from
 * the JWT.</p>
 */
@RestController
@RequestMapping("/api/v1/rides/{rideId}")
@RequiredArgsConstructor
public class BoardingStatusController {

    private final BoardingService boardingService;

    @GetMapping("/boarding-status")
    public ResponseEntity<BoardingStatusResponse> getBoardingStatus(
            @PathVariable UUID rideId, Authentication authentication) {

        UUID driverId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(boardingService.getBoardingStatus(driverId, rideId));
    }
}
