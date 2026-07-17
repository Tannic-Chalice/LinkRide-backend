package com.linkride.backend.booking;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Booking actions that aren't scoped to a single {@code rideId} path segment — cross-ride reads
 * (Phase 3.3: a passenger's own bookings span many different rides) plus cancellation (Phase 3.5:
 * either the passenger or the ride's driver may cancel, so it doesn't belong in
 * {@link BookingDecisionController}, which is driver-only). Ride-scoped booking actions live in
 * {@link BookingController} instead.
 *
 * <p>Security: all routes require a valid Supabase JWT (enforced globally by
 * {@link com.linkride.backend.config.SecurityConfig}). The caller's UUID is always resolved from
 * the JWT — never from the request body.</p>
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingQueryController {

    private final BookingService bookingService;

    /** The authenticated caller's own bookings across all rides, optionally filtered by status. */
    @GetMapping("/mine")
    public ResponseEntity<List<BookingResponse>> listMyBookings(
            @RequestParam(required = false) BookingStatus status,
            Authentication authentication) {

        UUID passengerId = UUID.fromString(authentication.getName());
        List<BookingResponse> response = bookingService.listMyBookings(passengerId, status);
        return ResponseEntity.ok(response);
    }

    /** Visible to the booking's own passenger or the ride's driver — nobody else. */
    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> getBooking(
            @PathVariable UUID bookingId,
            Authentication authentication) {

        UUID requesterId = UUID.fromString(authentication.getName());
        BookingResponse response = bookingService.getBooking(requesterId, bookingId);
        return ResponseEntity.ok(response);
    }

    /** Either the booking's own passenger or the ride's driver may cancel it. */
    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable UUID bookingId,
            Authentication authentication) {

        UUID requesterId = UUID.fromString(authentication.getName());
        BookingResponse response = bookingService.cancelBooking(requesterId, bookingId);
        return ResponseEntity.ok(response);
    }
}
