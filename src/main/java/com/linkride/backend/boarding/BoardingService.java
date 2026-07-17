package com.linkride.backend.boarding;

import com.linkride.backend.booking.BookingResponse;

import java.util.UUID;

public interface BoardingService {

    /**
     * Driver-only: returns the booking's currently active QR if one exists and hasn't expired,
     * otherwise mints a new one. The booking must be {@code ACCEPTED} and its ride still
     * {@code SCHEDULED}.
     */
    BoardingQrResponse issueQr(UUID driverId, UUID bookingId);

    /**
     * Driver-only: always mints a fresh QR, revoking whatever was previously active for this
     * booking — for when a scan visibly failed and the driver wants a new code rather than
     * waiting out the old one's TTL.
     */
    BoardingQrResponse refreshQr(UUID driverId, UUID bookingId);

    /**
     * Passenger-only: the core boarding transaction. Validates the scanned token belongs to this
     * passenger's own booking, hasn't expired/been revoked/already been consumed, and that the
     * booking/ride are still boardable — then transitions the booking {@code ACCEPTED ->
     * CHECKED_IN}, consumes the token, and records the verification, all atomically.
     */
    BookingResponse checkInWithToken(UUID passengerId, String token);

    /**
     * Driver-only: generates a fresh OTP for this booking, overwriting/resetting any previous one
     * (a driver-initiated "resend"), and sends it to the passenger. Same preconditions as
     * {@link #issueQr} — {@code ACCEPTED} booking, {@code SCHEDULED} ride.
     */
    OtpGenerateResponse generateOtp(UUID driverId, UUID bookingId);

    /**
     * Driver-only: the driver reads back the code the passenger received and submits it here on
     * the passenger's behalf. On a correct, unexpired, not-yet-locked-out code, transitions the
     * booking {@code ACCEPTED -> CHECKED_IN} exactly like {@link #checkInWithToken}, just via the
     * OTP path instead of the QR path.
     */
    BookingResponse verifyOtp(UUID driverId, UUID bookingId, String otp);

    /**
     * Driver-only: the ride's current boarding progress — what the driver's app polls every few
     * seconds while boarding is in progress. Unlike the other driver-only methods here, this is a
     * plain read with no state precondition: it reflects whatever the ride's current status
     * actually is, even after the driver has already started/cancelled/completed it.
     */
    BoardingStatusResponse getBoardingStatus(UUID driverId, UUID rideId);
}
