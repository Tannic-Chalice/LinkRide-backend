package com.linkride.backend.booking;

import com.linkride.backend.dto.ErrorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Ride-scoped booking actions: passenger request-to-book (Phase 3.2) and the driver's view of
 * requests against their own ride (Phase 3.3). Accept/reject (3.4) and cancellation (3.5) land in
 * later Phase 3 milestones. Passenger's cross-ride booking list/detail live in
 * {@link BookingQueryController} instead — they don't hang off a {@code rideId} path segment.
 *
 * <p>Security: all routes require a valid Supabase JWT (enforced globally by
 * {@link com.linkride.backend.config.SecurityConfig}). The caller's UUID is always resolved from
 * the JWT — never from the request body.</p>
 */
@RestController
@RequestMapping("/api/rides/{rideId}/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<?> requestBooking(
            @PathVariable UUID rideId,
            @Valid @RequestBody BookingCreateRequest request,
            Authentication authentication) {

        try {
            UUID passengerId = UUID.fromString(authentication.getName());
            BookingResponse response = bookingService.requestBooking(passengerId, rideId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("VALIDATION_ERROR", e.getMessage()));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(new ErrorResponse(e.getStatusCode().toString(), e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
        }
    }

    /** Driver-only — every booking against a ride they drive, optionally filtered by status. */
    @GetMapping
    public ResponseEntity<?> listBookingsForRide(
            @PathVariable UUID rideId,
            @RequestParam(required = false) BookingStatus status,
            Authentication authentication) {

        try {
            UUID driverId = UUID.fromString(authentication.getName());
            List<BookingResponse> response = bookingService.listBookingsForRide(driverId, rideId, status);
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
