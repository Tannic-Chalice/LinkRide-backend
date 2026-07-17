package com.linkride.backend.boarding;

import com.linkride.backend.booking.BookingResponse;
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
 * Driver-side boarding actions: QR issuance (Phase 4.2) and the driver-assisted OTP fallback
 * (Phase 4.4), both for a specific passenger's booking. Passenger check-in (scanning the QR) lives
 * in a separate controller instead (Phase 4.3) — the actor and authorization differ (driver here,
 * passenger there), matching the split already used between
 * {@link com.linkride.backend.booking.BookingController} and
 * {@link com.linkride.backend.booking.BookingDecisionController}.
 *
 * <p>Security: all routes require a valid Supabase JWT (enforced globally by
 * {@link com.linkride.backend.config.SecurityConfig}). The caller's UUID is always resolved from
 * the JWT — never from the request body.</p>
 */
@RestController
@RequestMapping("/api/bookings/{bookingId}/boarding")
@RequiredArgsConstructor
public class BoardingController {

    private final BoardingService boardingService;

    @PostMapping("/qr")
    public ResponseEntity<BoardingQrResponse> issueQr(@PathVariable UUID bookingId, Authentication authentication) {
        UUID driverId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(boardingService.issueQr(driverId, bookingId));
    }

    @PostMapping("/qr/refresh")
    public ResponseEntity<BoardingQrResponse> refreshQr(@PathVariable UUID bookingId, Authentication authentication) {
        UUID driverId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(boardingService.refreshQr(driverId, bookingId));
    }

    @PostMapping("/otp")
    public ResponseEntity<OtpGenerateResponse> generateOtp(@PathVariable UUID bookingId, Authentication authentication) {
        UUID driverId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(boardingService.generateOtp(driverId, bookingId));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<BookingResponse> verifyOtp(
            @PathVariable UUID bookingId,
            @Valid @RequestBody OtpVerifyRequest request,
            Authentication authentication) {

        UUID driverId = UUID.fromString(authentication.getName());
        BookingResponse response = boardingService.verifyOtp(driverId, bookingId, request.getOtp());
        return ResponseEntity.ok(response);
    }
}
