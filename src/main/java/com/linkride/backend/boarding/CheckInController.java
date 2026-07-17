package com.linkride.backend.boarding;

import com.linkride.backend.booking.BookingResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Passenger-side boarding action (Phase 4.3): scanning the driver's QR. Separate from
 * {@link BoardingController} since the actor and authorization differ (passenger here, driver
 * there) — same split as {@code BookingController} vs. {@code BookingDecisionController}.
 *
 * <p>Security: requires a valid Supabase JWT (enforced globally by
 * {@link com.linkride.backend.config.SecurityConfig}). The caller's UUID is always resolved from
 * the JWT. No ride/booking/driver id is ever supplied by the client — everything is derived from
 * the token itself plus the authenticated passenger.</p>
 */
@RestController
@RequestMapping("/api/boarding")
@RequiredArgsConstructor
public class CheckInController {

    private final BoardingService boardingService;

    @PostMapping("/check-in")
    public ResponseEntity<BookingResponse> checkIn(
            @Valid @RequestBody CheckInRequest request,
            Authentication authentication) {

        UUID passengerId = UUID.fromString(authentication.getName());
        BookingResponse response = boardingService.checkInWithToken(passengerId, request.getToken());
        return ResponseEntity.ok(response);
    }
}
