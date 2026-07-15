package com.linkride.backend.booking;

import com.linkride.backend.dto.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Driver decisions on a booking (Phase 3.4) — separate from {@link BookingQueryController}
 * (reads) since these are mutations, and separate from {@link BookingController} since they act
 * on a booking by id, not scoped under a {@code rideId} path segment.
 *
 * <p>Security: all routes require a valid Supabase JWT (enforced globally by
 * {@link com.linkride.backend.config.SecurityConfig}). The caller's UUID is always resolved from
 * the JWT — never from the request body.</p>
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingDecisionController {

    private final BookingService bookingService;

    @PostMapping("/{bookingId}/accept")
    public ResponseEntity<?> acceptBooking(@PathVariable UUID bookingId, Authentication authentication) {
        try {
            UUID driverId = UUID.fromString(authentication.getName());
            BookingResponse response = bookingService.acceptBooking(driverId, bookingId);
            return ResponseEntity.ok(response);

        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(new ErrorResponse(e.getStatusCode().toString(), e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
        }
    }

    @PostMapping("/{bookingId}/reject")
    public ResponseEntity<?> rejectBooking(@PathVariable UUID bookingId, Authentication authentication) {
        try {
            UUID driverId = UUID.fromString(authentication.getName());
            BookingResponse response = bookingService.rejectBooking(driverId, bookingId);
            return ResponseEntity.ok(response);

        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(new ErrorResponse(e.getStatusCode().toString(), e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
        }
    }
}
