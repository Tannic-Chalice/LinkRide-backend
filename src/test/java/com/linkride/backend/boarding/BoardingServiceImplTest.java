package com.linkride.backend.boarding;

import com.linkride.backend.booking.Booking;
import com.linkride.backend.booking.BookingRepository;
import com.linkride.backend.booking.BookingStatus;
import com.linkride.backend.entity.User;
import com.linkride.backend.location.GeoPoint;
import com.linkride.backend.ride.Ride;
import com.linkride.backend.ride.RideRepository;
import com.linkride.backend.ride.RideStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.assertj.core.groups.Tuple;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardingServiceImplTest {

    @Mock
    private BoardingTokenRepository boardingTokenRepository;
    @Mock
    private BoardingVerificationRepository boardingVerificationRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private RideRepository rideRepository;
    @Mock
    private OtpNotificationService otpNotificationService;

    private BoardingServiceImpl boardingService;

    private UUID driverId;
    private UUID passengerId;
    private UUID rideId;
    private UUID bookingId;
    private User driver;
    private Ride ride;
    private Booking booking;

    @BeforeEach
    void setUp() {
        BoardingProperties boardingProperties = new BoardingProperties();
        boardingService = new BoardingServiceImpl(
                boardingTokenRepository, boardingVerificationRepository, bookingRepository, rideRepository,
                boardingProperties, otpNotificationService);

        driverId = UUID.randomUUID();
        passengerId = UUID.randomUUID();
        rideId = UUID.randomUUID();
        bookingId = UUID.randomUUID();

        driver = new User();
        driver.setId(driverId);

        User passenger = new User();
        passenger.setId(passengerId);

        ride = new Ride();
        ride.setRideId(rideId);
        ride.setDriver(driver);
        ride.setStatus(RideStatus.SCHEDULED);
        ride.setPickup(new GeoPoint("Test Pickup", "123 Test St", null));
        ride.setDestination(new GeoPoint("Test Destination", "456 Test Ave", null));

        booking = new Booking();
        booking.setBookingId(bookingId);
        booking.setRide(ride);
        booking.setPassenger(passenger);
        booking.setStatus(BookingStatus.ACCEPTED);
        booking.setPickupLat(12.97);
        booking.setPickupLng(77.59);
        booking.setDropLat(13.02);
        booking.setDropLng(77.63);

        lenient().when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        lenient().when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        lenient().when(boardingTokenRepository
                        .findByBooking_BookingIdAndRevokedAtIsNullAndConsumedAtIsNull(bookingId))
                .thenReturn(Optional.empty());
        lenient().when(boardingTokenRepository.saveAndFlush(any(BoardingToken.class)))
                .thenAnswer(inv -> {
                    BoardingToken t = inv.getArgument(0);
                    if (t.getTokenId() == null) {
                        t.setTokenId(UUID.randomUUID());
                    }
                    return t;
                });
        lenient().when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(boardingVerificationRepository.findByBooking_BookingId(bookingId))
                .thenReturn(Optional.empty());
    }

    private BoardingToken activeToken(OffsetDateTime expiresAt) {
        BoardingToken token = new BoardingToken();
        token.setTokenId(UUID.randomUUID());
        token.setToken("existing-token");
        token.setBooking(booking);
        token.setCreatedBy(driver);
        token.setExpiresAt(expiresAt);
        return token;
    }

    private BoardingToken consumableToken() {
        BoardingToken token = new BoardingToken();
        token.setTokenId(UUID.randomUUID());
        token.setToken("scan-me");
        token.setBooking(booking);
        token.setCreatedBy(driver);
        token.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
        return token;
    }

    @Test
    void issueQr_noExistingToken_mintsNewToken() {
        BoardingQrResponse response = boardingService.issueQr(driverId, bookingId);

        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getExpiresAt()).isAfter(OffsetDateTime.now());
        verify(boardingTokenRepository).saveAndFlush(any(BoardingToken.class));
    }

    @Test
    void issueQr_activeUnexpiredTokenExists_returnsSameTokenWithoutMintingNew() {
        BoardingToken existing = activeToken(OffsetDateTime.now().plusMinutes(3));
        when(boardingTokenRepository.findByBooking_BookingIdAndRevokedAtIsNullAndConsumedAtIsNull(bookingId))
                .thenReturn(Optional.of(existing));

        BoardingQrResponse response = boardingService.issueQr(driverId, bookingId);

        assertThat(response.getToken()).isEqualTo("existing-token");
        verify(boardingTokenRepository, never()).saveAndFlush(any(BoardingToken.class));
    }

    @Test
    void issueQr_activeTokenExpired_revokesItAndMintsNew() {
        BoardingToken expired = activeToken(OffsetDateTime.now().minusMinutes(1));
        when(boardingTokenRepository.findByBooking_BookingIdAndRevokedAtIsNullAndConsumedAtIsNull(bookingId))
                .thenReturn(Optional.of(expired));

        BoardingQrResponse response = boardingService.issueQr(driverId, bookingId);

        assertThat(response.getToken()).isNotEqualTo("existing-token");
        assertThat(expired.getRevokedAt()).isNotNull();
        verify(boardingTokenRepository).save(expired);
        verify(boardingTokenRepository).saveAndFlush(any(BoardingToken.class));
    }

    @Test
    void issueQr_bookingNotFound_throws404() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> boardingService.issueQr(driverId, bookingId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Booking not found");
    }

    @Test
    void issueQr_notTheDriver_throws403() {
        UUID strangerId = UUID.randomUUID();

        assertThatThrownBy(() -> boardingService.issueQr(strangerId, bookingId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("do not drive");
    }

    @Test
    void issueQr_bookingNotAccepted_throws409() {
        booking.setStatus(BookingStatus.PENDING);

        assertThatThrownBy(() -> boardingService.issueQr(driverId, bookingId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not accepted");
    }

    @Test
    void issueQr_rideNotScheduled_throws409() {
        ride.setStatus(RideStatus.IN_PROGRESS);

        assertThatThrownBy(() -> boardingService.issueQr(driverId, bookingId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no longer open for boarding");
    }

    @Test
    void issueQr_concurrentIssueRace_savesFlushThrowsConstraintViolation_throws409() {
        when(boardingTokenRepository.saveAndFlush(any(BoardingToken.class)))
                .thenThrow(new DataIntegrityViolationException("uq_boarding_tokens_active_per_booking"));

        assertThatThrownBy(() -> boardingService.issueQr(driverId, bookingId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already generated");
    }

    @Test
    void refreshQr_activeUnexpiredTokenExists_revokesItAnywayAndMintsNew() {
        BoardingToken existing = activeToken(OffsetDateTime.now().plusMinutes(3));
        when(boardingTokenRepository.findByBooking_BookingIdAndRevokedAtIsNullAndConsumedAtIsNull(bookingId))
                .thenReturn(Optional.of(existing));

        BoardingQrResponse response = boardingService.refreshQr(driverId, bookingId);

        assertThat(existing.getRevokedAt()).isNotNull();
        assertThat(response.getToken()).isNotEqualTo("existing-token");
        verify(boardingTokenRepository).save(existing);
    }

    @Test
    void refreshQr_noExistingToken_mintsNewTokenWithoutRevoking() {
        boardingService.refreshQr(driverId, bookingId);

        verify(boardingTokenRepository, never()).save(any(BoardingToken.class));
        verify(boardingTokenRepository).saveAndFlush(any(BoardingToken.class));
    }

    @Test
    void refreshQr_notTheDriver_throws403() {
        UUID strangerId = UUID.randomUUID();

        assertThatThrownBy(() -> boardingService.refreshQr(strangerId, bookingId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("do not drive");
    }

    @Test
    void mintedToken_setsCreatedByFromRideDriverAndExpiresAtFromTtl() {
        ArgumentCaptor<BoardingToken> captor = ArgumentCaptor.forClass(BoardingToken.class);

        boardingService.issueQr(driverId, bookingId);

        verify(boardingTokenRepository).saveAndFlush(captor.capture());
        BoardingToken minted = captor.getValue();
        assertThat(minted.getCreatedBy()).isEqualTo(driver);
        assertThat(minted.getBooking()).isEqualTo(booking);
        assertThat(minted.getExpiresAt())
                .isCloseTo(OffsetDateTime.now().plusMinutes(5), within(10, ChronoUnit.SECONDS));
    }

    @Test
    void checkInWithToken_happyPath_transitionsBookingConsumesTokenAndRecordsVerification() {
        BoardingToken token = consumableToken();
        when(boardingTokenRepository.findByToken("scan-me")).thenReturn(Optional.of(token));

        var response = boardingService.checkInWithToken(passengerId, "scan-me");

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(token.getConsumedAt()).isNotNull();
        verify(boardingTokenRepository).save(token);

        ArgumentCaptor<BoardingVerification> captor = ArgumentCaptor.forClass(BoardingVerification.class);
        verify(boardingVerificationRepository).save(captor.capture());
        BoardingVerification verification = captor.getValue();
        assertThat(verification.getBooking()).isEqualTo(booking);
        assertThat(verification.getCheckedInAt()).isNotNull();
        assertThat(verification.getCheckedInVia()).isEqualTo(CheckedInVia.QR);
    }

    @Test
    void checkInWithToken_reusesExistingVerificationRowRatherThanCreatingASecondOne() {
        BoardingToken token = consumableToken();
        when(boardingTokenRepository.findByToken("scan-me")).thenReturn(Optional.of(token));

        BoardingVerification existing = new BoardingVerification();
        existing.setBooking(booking);
        when(boardingVerificationRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.of(existing));

        boardingService.checkInWithToken(passengerId, "scan-me");

        verify(boardingVerificationRepository).save(existing);
        assertThat(existing.getCheckedInVia()).isEqualTo(CheckedInVia.QR);
    }

    @Test
    void checkInWithToken_tokenNotFound_throws404() {
        when(boardingTokenRepository.findByToken("bogus")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> boardingService.checkInWithToken(passengerId, "bogus"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid QR code");
    }

    @Test
    void checkInWithToken_wrongPassenger_throws403() {
        BoardingToken token = consumableToken();
        when(boardingTokenRepository.findByToken("scan-me")).thenReturn(Optional.of(token));

        UUID strangerId = UUID.randomUUID();

        assertThatThrownBy(() -> boardingService.checkInWithToken(strangerId, "scan-me"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("isn't for your booking");
    }

    @Test
    void checkInWithToken_revokedToken_throws409() {
        BoardingToken token = consumableToken();
        token.setRevokedAt(OffsetDateTime.now());
        when(boardingTokenRepository.findByToken("scan-me")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> boardingService.checkInWithToken(passengerId, "scan-me"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no longer valid");
    }

    @Test
    void checkInWithToken_alreadyConsumedToken_throws409() {
        BoardingToken token = consumableToken();
        token.setConsumedAt(OffsetDateTime.now());
        when(boardingTokenRepository.findByToken("scan-me")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> boardingService.checkInWithToken(passengerId, "scan-me"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void checkInWithToken_expiredToken_throws409() {
        BoardingToken token = consumableToken();
        token.setExpiresAt(OffsetDateTime.now().minusSeconds(1));
        when(boardingTokenRepository.findByToken("scan-me")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> boardingService.checkInWithToken(passengerId, "scan-me"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void checkInWithToken_bookingNotAccepted_throws409() {
        BoardingToken token = consumableToken();
        when(boardingTokenRepository.findByToken("scan-me")).thenReturn(Optional.of(token));
        booking.setStatus(BookingStatus.CHECKED_IN);

        assertThatThrownBy(() -> boardingService.checkInWithToken(passengerId, "scan-me"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not accepted");
    }

    @Test
    void checkInWithToken_rideNotScheduled_throws409() {
        BoardingToken token = consumableToken();
        when(boardingTokenRepository.findByToken("scan-me")).thenReturn(Optional.of(token));
        ride.setStatus(RideStatus.IN_PROGRESS);

        assertThatThrownBy(() -> boardingService.checkInWithToken(passengerId, "scan-me"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no longer open for boarding");
    }

    @Test
    void checkInWithToken_concurrentModificationOfBooking_throws409AndNeverTouchesTokenOrVerification() {
        BoardingToken token = consumableToken();
        when(boardingTokenRepository.findByToken("scan-me")).thenReturn(Optional.of(token));
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Booking.class, bookingId));

        assertThatThrownBy(() -> boardingService.checkInWithToken(passengerId, "scan-me"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already updated by another request");

        assertThat(token.getConsumedAt()).isNull();
        verify(boardingTokenRepository, never()).save(any(BoardingToken.class));
        verify(boardingVerificationRepository, never()).save(any(BoardingVerification.class));
    }

    @Test
    void generateOtp_happyPath_savesHashedOtpAndSendsToPassenger() {
        OtpGenerateResponse response = boardingService.generateOtp(driverId, bookingId);

        assertThat(response.getExpiresAt()).isCloseTo(OffsetDateTime.now().plusMinutes(10), within(10, ChronoUnit.SECONDS));

        ArgumentCaptor<BoardingVerification> captor = ArgumentCaptor.forClass(BoardingVerification.class);
        verify(boardingVerificationRepository).saveAndFlush(captor.capture());
        BoardingVerification verification = captor.getValue();
        assertThat(verification.getOtpHash()).isNotBlank();
        assertThat(verification.getOtpAttempts()).isZero();
        assertThat(verification.getOtpExpiresAt()).isAfter(OffsetDateTime.now());

        verify(otpNotificationService).sendOtp(eq(booking.getPassenger()), any());
    }

    @Test
    void generateOtp_reusesExistingVerificationRowAndResetsAttempts() {
        BoardingVerification existing = new BoardingVerification();
        existing.setBooking(booking);
        existing.setOtpAttempts(3);
        when(boardingVerificationRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.of(existing));

        boardingService.generateOtp(driverId, bookingId);

        verify(boardingVerificationRepository).saveAndFlush(existing);
        assertThat(existing.getOtpAttempts()).isZero();
    }

    @Test
    void generateOtp_notTheDriver_throws403() {
        UUID strangerId = UUID.randomUUID();

        assertThatThrownBy(() -> boardingService.generateOtp(strangerId, bookingId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("do not drive");
    }

    @Test
    void generateOtp_bookingNotAccepted_throws409() {
        booking.setStatus(BookingStatus.PENDING);

        assertThatThrownBy(() -> boardingService.generateOtp(driverId, bookingId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not accepted");
    }

    @Test
    void generateOtp_concurrentGenerateRace_savesFlushThrowsConstraintViolation_throws409() {
        when(boardingVerificationRepository.saveAndFlush(any(BoardingVerification.class)))
                .thenThrow(new DataIntegrityViolationException("boarding_verifications_booking_id_key"));

        assertThatThrownBy(() -> boardingService.generateOtp(driverId, bookingId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already in progress");

        verify(otpNotificationService, never()).sendOtp(any(), any());
    }

    private BoardingVerification verificationWithOtp(String otp) {
        BoardingVerification verification = new BoardingVerification();
        verification.setBooking(booking);
        verification.setOtpHash(sha256(otp));
        verification.setOtpExpiresAt(OffsetDateTime.now().plusMinutes(10));
        verification.setOtpAttempts(0);
        return verification;
    }

    private static String sha256(String value) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void verifyOtp_correctCode_transitionsBookingAndRecordsVerification() {
        BoardingVerification verification = verificationWithOtp("123456");
        when(boardingVerificationRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.of(verification));

        var response = boardingService.verifyOtp(driverId, bookingId, "123456");

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(verification.getCheckedInAt()).isNotNull();
        assertThat(verification.getCheckedInVia()).isEqualTo(CheckedInVia.OTP);
    }

    @Test
    void verifyOtp_wrongCode_incrementsAttemptsAndThrows409WithRemainingCount() {
        BoardingVerification verification = verificationWithOtp("123456");
        when(boardingVerificationRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> boardingService.verifyOtp(driverId, bookingId, "000000"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("4 attempt(s) remaining");

        assertThat(verification.getOtpAttempts()).isEqualTo(1);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.ACCEPTED);
    }

    @Test
    void verifyOtp_fifthWrongAttempt_stillCheckedAndReportsZeroRemaining() {
        BoardingVerification verification = verificationWithOtp("123456");
        verification.setOtpAttempts(4);
        when(boardingVerificationRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> boardingService.verifyOtp(driverId, bookingId, "000000"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("0 attempt(s) remaining");

        assertThat(verification.getOtpAttempts()).isEqualTo(5);
    }

    @Test
    void verifyOtp_maxAttemptsReached_locksOutWithoutCheckingCode() {
        BoardingVerification verification = verificationWithOtp("123456");
        verification.setOtpAttempts(5);
        when(boardingVerificationRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> boardingService.verifyOtp(driverId, bookingId, "123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Too many incorrect attempts");
    }

    @Test
    void verifyOtp_expiredOtp_throws409() {
        BoardingVerification verification = verificationWithOtp("123456");
        verification.setOtpExpiresAt(OffsetDateTime.now().minusSeconds(1));
        when(boardingVerificationRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> boardingService.verifyOtp(driverId, bookingId, "123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verifyOtp_noOtpEverGenerated_throws409() {
        assertThatThrownBy(() -> boardingService.verifyOtp(driverId, bookingId, "123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No OTP has been generated");
    }

    @Test
    void verifyOtp_notTheDriver_throws403() {
        UUID strangerId = UUID.randomUUID();

        assertThatThrownBy(() -> boardingService.verifyOtp(strangerId, bookingId, "123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("do not drive");
    }

    @Test
    void verifyOtp_concurrentModificationOfBooking_throws409AndDoesNotRecordVerification() {
        BoardingVerification verification = verificationWithOtp("123456");
        when(boardingVerificationRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.of(verification));
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Booking.class, bookingId));

        assertThatThrownBy(() -> boardingService.verifyOtp(driverId, bookingId, "123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already updated by another request");

        assertThat(verification.getCheckedInAt()).isNull();
    }

    private Booking bookingWithPassenger(String name, BookingStatus status) {
        User passenger = new User();
        passenger.setId(UUID.randomUUID());
        passenger.setFullName(name);

        Booking b = new Booking();
        b.setBookingId(UUID.randomUUID());
        b.setRide(ride);
        b.setPassenger(passenger);
        b.setStatus(status);
        return b;
    }

    @Test
    void getBoardingStatus_happyPath_returnsCountsCanStartAndPassengerList() {
        Booking checkedIn = bookingWithPassenger("Joseph", BookingStatus.CHECKED_IN);
        Booking accepted = bookingWithPassenger("Aniket", BookingStatus.ACCEPTED);
        when(bookingRepository.findBoardingCandidatesForRide(rideId)).thenReturn(List.of(checkedIn, accepted));

        BoardingStatusResponse response = boardingService.getBoardingStatus(driverId, rideId);

        assertThat(response.getRideStatus()).isEqualTo(RideStatus.SCHEDULED);
        assertThat(response.getCheckedIn()).isEqualTo(1);
        assertThat(response.getTotalPassengers()).isEqualTo(2);
        assertThat(response.isCanStartRide()).isTrue();
        assertThat(response.getPassengers())
                .extracting(PassengerBoardingDto::getName, PassengerBoardingDto::getStatus)
                .containsExactlyInAnyOrder(
                        Tuple.tuple("Joseph", BookingStatus.CHECKED_IN),
                        Tuple.tuple("Aniket", BookingStatus.ACCEPTED));
    }

    @Test
    void getBoardingStatus_noBookingsYet_returnsZeroCountsAndEmptyList() {
        when(bookingRepository.findBoardingCandidatesForRide(rideId)).thenReturn(List.of());

        BoardingStatusResponse response = boardingService.getBoardingStatus(driverId, rideId);

        assertThat(response.getCheckedIn()).isZero();
        assertThat(response.getTotalPassengers()).isZero();
        assertThat(response.getPassengers()).isEmpty();
        assertThat(response.isCanStartRide()).isTrue();
    }

    @Test
    void getBoardingStatus_rideNoLongerScheduled_canStartRideIsFalse() {
        ride.setStatus(RideStatus.IN_PROGRESS);
        when(bookingRepository.findBoardingCandidatesForRide(rideId)).thenReturn(List.of());

        BoardingStatusResponse response = boardingService.getBoardingStatus(driverId, rideId);

        assertThat(response.getRideStatus()).isEqualTo(RideStatus.IN_PROGRESS);
        assertThat(response.isCanStartRide()).isFalse();
    }

    @Test
    void getBoardingStatus_rideNotFound_throws404() {
        when(rideRepository.findById(rideId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> boardingService.getBoardingStatus(driverId, rideId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ride not found");
    }

    @Test
    void getBoardingStatus_notTheDriver_throws403() {
        UUID strangerId = UUID.randomUUID();

        assertThatThrownBy(() -> boardingService.getBoardingStatus(strangerId, rideId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("do not drive");
    }
}
