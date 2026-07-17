package com.linkride.backend.boarding;

import com.linkride.backend.booking.Booking;
import com.linkride.backend.booking.BookingRepository;
import com.linkride.backend.booking.BookingResponse;
import com.linkride.backend.booking.BookingStatus;
import com.linkride.backend.ride.Ride;
import com.linkride.backend.ride.RideRepository;
import com.linkride.backend.ride.RideStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BoardingServiceImpl implements BoardingService {

    private static final String CONCURRENT_ISSUE_MESSAGE =
            "A QR was already generated for this booking, please retry";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final BoardingTokenRepository boardingTokenRepository;
    private final BoardingVerificationRepository boardingVerificationRepository;
    private final BookingRepository bookingRepository;
    private final RideRepository rideRepository;
    private final BoardingProperties boardingProperties;
    private final OtpNotificationService otpNotificationService;

    /**
     * Reuses the active token if one exists and hasn't expired yet — driver taps on the same
     * passenger card again while it's still on screen. A time-expired-but-not-yet-revoked token
     * is explicitly revoked here before minting a new one, keeping
     * {@code uq_boarding_tokens_active_per_booking}'s invariant ("at most one live token per
     * booking") true regardless of expiry.
     */
    @Override
    @Transactional
    public BoardingQrResponse issueQr(UUID driverId, UUID bookingId) {
        Booking booking = loadAuthorizedBoardableBooking(driverId, bookingId);

        Optional<BoardingToken> active = boardingTokenRepository
                .findByBooking_BookingIdAndRevokedAtIsNullAndConsumedAtIsNull(bookingId);

        if (active.isPresent() && active.get().getExpiresAt().isAfter(OffsetDateTime.now())) {
            return BoardingQrResponse.from(active.get());
        }

        active.ifPresent(this::revoke);

        return BoardingQrResponse.from(mintToken(booking));
    }

    /** Unconditionally revokes whatever's currently active (even if still unexpired) and mints a new one. */
    @Override
    @Transactional
    public BoardingQrResponse refreshQr(UUID driverId, UUID bookingId) {
        Booking booking = loadAuthorizedBoardableBooking(driverId, bookingId);

        boardingTokenRepository
                .findByBooking_BookingIdAndRevokedAtIsNullAndConsumedAtIsNull(bookingId)
                .ifPresent(this::revoke);

        return BoardingQrResponse.from(mintToken(booking));
    }

    /**
     * Shared preconditions for both {@link #issueQr} and {@link #refreshQr}: only the ride's own
     * driver may issue a QR, only for a booking that's actually boardable — {@code ACCEPTED}, on a
     * ride still {@code SCHEDULED}.
     */
    private Booking loadAuthorizedBoardableBooking(UUID driverId, UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        Ride ride = booking.getRide();

        if (!ride.getDriver().getId().equals(driverId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not drive this ride");
        }

        if (booking.getStatus() != BookingStatus.ACCEPTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This booking is not accepted, so it cannot be boarded");
        }

        if (ride.getStatus() != RideStatus.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This ride is no longer open for boarding");
        }

        return booking;
    }

    /**
     * The core boarding transaction (Phase 4.3). Validates the scanned token belongs to this
     * passenger's own booking, hasn't expired/been revoked/already been consumed, and that the
     * booking/ride are still boardable — then transitions the booking, consumes the token, and
     * records the verification, all in one transaction.
     *
     * <p>{@code Booking}'s {@code @Version} is what actually serializes concurrent attempts (e.g.
     * the same QR scanned twice within milliseconds): the loser's {@code saveAndFlush} below
     * throws before either the token or the verification is ever touched, so at most one of two
     * racing scans can succeed, and the loser leaves no trace — the whole transaction, including
     * any in-memory changes made before the throw, rolls back.</p>
     */
    @Override
    @Transactional
    public BookingResponse checkInWithToken(UUID passengerId, String tokenValue) {
        BoardingToken token = boardingTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid QR code"));

        Booking booking = token.getBooking();

        if (!booking.getPassenger().getId().equals(passengerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This code isn't for your booking");
        }

        if (token.getRevokedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This QR code is no longer valid");
        }

        if (token.getConsumedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This QR code has already been used");
        }

        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This QR code has expired");
        }

        if (booking.getStatus() != BookingStatus.ACCEPTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This booking is not accepted, so it cannot be boarded");
        }

        Ride ride = booking.getRide();
        if (ride.getStatus() != RideStatus.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This ride is no longer open for boarding");
        }

        BoardingVerification verification = findOrCreateVerification(booking);
        completeCheckIn(booking, verification, CheckedInVia.QR);

        token.setConsumedAt(verification.getCheckedInAt());
        boardingTokenRepository.save(token);

        return BookingResponse.from(booking);
    }

    /**
     * Driver-only (Phase 4.4): generates a fresh OTP for this booking — overwriting/resetting any
     * previous one, so this doubles as a driver-initiated "resend" — and sends it to the
     * passenger. Same preconditions as {@link #issueQr}: {@code ACCEPTED} booking, {@code
     * SCHEDULED} ride.
     *
     * <p>Unlike {@link #checkInWithToken}/{@link #verifyOtp}, there's no {@code Booking}-level
     * {@code @Version} guarding this write — {@code boarding_verifications.booking_id} is unique
     * at the database level instead (Phase 4.7 hardening), and {@code saveAndFlush} here surfaces
     * that as a clean 409 instead of letting a rare double-tap race fall through as an unhandled
     * constraint violation at commit time.</p>
     */
    @Override
    @Transactional
    public OtpGenerateResponse generateOtp(UUID driverId, UUID bookingId) {
        Booking booking = loadAuthorizedBoardableBooking(driverId, bookingId);

        String otp = generateOtpValue();

        BoardingVerification verification = findOrCreateVerification(booking);
        verification.setOtpHash(hashOtp(otp));
        verification.setOtpExpiresAt(OffsetDateTime.now().plusMinutes(boardingProperties.getOtpTtlMinutes()));
        verification.setOtpAttempts(0);

        try {
            boardingVerificationRepository.saveAndFlush(verification);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "An OTP request is already in progress for this booking, please retry");
        }

        otpNotificationService.sendOtp(booking.getPassenger(), otp);

        return OtpGenerateResponse.builder().expiresAt(verification.getOtpExpiresAt()).build();
    }

    /**
     * Driver-only (Phase 4.4): the driver reads back the code the passenger received and submits
     * it here. A wrong code just increments the attempt counter and rejects — it never touches
     * {@code Booking}, so there's no concurrency concern on that path. A correct code runs through
     * the exact same {@link #completeCheckIn} used by {@link #checkInWithToken}, so the same
     * {@code @Version}-based guarantee applies here too.
     */
    @Override
    @Transactional
    public BookingResponse verifyOtp(UUID driverId, UUID bookingId, String submittedOtp) {
        Booking booking = loadAuthorizedBoardableBooking(driverId, bookingId);

        BoardingVerification verification = boardingVerificationRepository
                .findByBooking_BookingId(bookingId)
                .filter(v -> v.getOtpHash() != null)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "No OTP has been generated for this booking"));

        if (verification.getOtpExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This OTP has expired, generate a new one");
        }

        if (verification.getOtpAttempts() >= boardingProperties.getOtpMaxAttempts()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Too many incorrect attempts, generate a new OTP");
        }

        if (!hashOtp(submittedOtp).equals(verification.getOtpHash())) {
            verification.setOtpAttempts(verification.getOtpAttempts() + 1);
            boardingVerificationRepository.save(verification);
            int remaining = boardingProperties.getOtpMaxAttempts() - verification.getOtpAttempts();
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Incorrect OTP, " + remaining + " attempt(s) remaining");
        }

        completeCheckIn(booking, verification, CheckedInVia.OTP);

        return BookingResponse.from(booking);
    }

    /**
     * Driver-only read (Phase 4.5) — no state precondition, unlike every other driver-only method
     * here: the driver's app polls this continuously through boarding and past it, so it must
     * keep returning a coherent answer whatever the ride's current status actually is.
     */
    @Override
    @Transactional(readOnly = true)
    public BoardingStatusResponse getBoardingStatus(UUID driverId, UUID rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));

        if (!ride.getDriver().getId().equals(driverId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not drive this ride");
        }

        List<Booking> boardingCandidates = bookingRepository.findBoardingCandidatesForRide(rideId);

        long checkedIn = boardingCandidates.stream()
                .filter(b -> b.getStatus() == BookingStatus.CHECKED_IN)
                .count();

        List<PassengerBoardingDto> passengers = boardingCandidates.stream()
                .map(PassengerBoardingDto::from)
                .toList();

        return BoardingStatusResponse.builder()
                .rideStatus(ride.getStatus())
                .checkedIn((int) checkedIn)
                .totalPassengers(boardingCandidates.size())
                .canStartRide(ride.getStatus() == RideStatus.SCHEDULED)
                .passengers(passengers)
                .build();
    }

    private BoardingVerification findOrCreateVerification(Booking booking) {
        return boardingVerificationRepository.findByBooking_BookingId(booking.getBookingId())
                .orElseGet(() -> {
                    BoardingVerification v = new BoardingVerification();
                    v.setBooking(booking);
                    return v;
                });
    }

    /**
     * The actual {@code ACCEPTED -> CHECKED_IN} transition, shared by both the QR and OTP paths.
     * {@code Booking}'s {@code @Version} is what serializes concurrent attempts (e.g. the same QR
     * scanned twice, or a QR scan racing a driver-submitted OTP for the same booking): the loser's
     * {@code saveAndFlush} throws before {@code verification} is ever written, so the whole
     * transaction — including any in-memory changes made before the throw — rolls back with no
     * trace left behind.
     */
    private void completeCheckIn(Booking booking, BoardingVerification verification, CheckedInVia via) {
        booking.setStatus(BookingStatus.CHECKED_IN);
        try {
            bookingRepository.saveAndFlush(booking);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This booking was already updated by another request");
        }

        OffsetDateTime checkedInAt = OffsetDateTime.now();
        verification.setCheckedInAt(checkedInAt);
        verification.setCheckedInVia(via);
        boardingVerificationRepository.save(verification);
    }

    private String generateOtpValue() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    /**
     * A plain, unsalted SHA-256 hash — deliberately not the point of protection here. A 6-digit
     * OTP's entire 1,000,000-value space is trivially precomputable regardless of hash choice; the
     * real defenses are {@link BoardingProperties#getOtpMaxAttempts()}, the short TTL, and that
     * only the ride's authenticated driver can ever submit an attempt. Hashing just keeps the
     * literal code out of the database/logs/backups.
     */
    private String hashOtp(String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(otp.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is a required JDK algorithm", e);
        }
    }

    private void revoke(BoardingToken token) {
        token.setRevokedAt(OffsetDateTime.now());
        boardingTokenRepository.save(token);
    }

    private BoardingToken mintToken(Booking booking) {
        BoardingToken token = new BoardingToken();
        token.setToken(generateTokenValue());
        token.setBooking(booking);
        // The ride's driver is already loaded (its id was just checked in
        // loadAuthorizedBoardableBooking) — reuse that reference instead of a second lookup.
        token.setCreatedBy(booking.getRide().getDriver());
        token.setExpiresAt(OffsetDateTime.now().plusMinutes(boardingProperties.getQrTtlMinutes()));

        try {
            // saveAndFlush so uq_boarding_tokens_active_per_booking is checked now, inside this
            // try block — same reasoning as BookingServiceImpl.requestBooking's saveAndFlush.
            return boardingTokenRepository.saveAndFlush(token);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, CONCURRENT_ISSUE_MESSAGE);
        }
    }

    private String generateTokenValue() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
