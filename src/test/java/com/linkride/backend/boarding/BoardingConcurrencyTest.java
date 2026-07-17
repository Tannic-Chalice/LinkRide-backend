package com.linkride.backend.boarding;

import com.linkride.backend.booking.Booking;
import com.linkride.backend.booking.BookingRepository;
import com.linkride.backend.booking.BookingStatus;
import com.linkride.backend.entity.User;
import com.linkride.backend.entity.Vehicle;
import com.linkride.backend.location.GeoPoint;
import com.linkride.backend.repository.UserRepository;
import com.linkride.backend.repository.VehicleRepository;
import com.linkride.backend.ride.Ride;
import com.linkride.backend.ride.RideRepository;
import com.linkride.backend.ride.RideStatus;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the same class of guarantee {@code BookingConcurrencyTest} proves for seat reservation,
 * but for the boarding transaction: that {@code Booking}'s {@code @Version} really does serialize
 * two real, concurrent scans of the identical QR token, not just that the code catches
 * {@code ObjectOptimisticLockingFailureException} correctly on a mocked repository.
 *
 * <p>See {@code BookingConcurrencyTest}'s javadoc for why this isn't a {@code *IT} class, why
 * {@code disabledWithoutDocker} is used, and why this runs against a throwaway
 * {@code create-drop} schema instead of the real Flyway chain.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED) // setup must actually commit — see class javadoc
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Testcontainers(disabledWithoutDocker = true)
// Same reasoning as BookingConcurrencyTest: @DataJpaTest won't pick up @Service beans on its own,
// so this registers the real BoardingServiceImpl (and its BoardingProperties dependency) as
// Spring-managed beans so @Transactional actually gets AOP-woven.
@Import({BoardingServiceImpl.class, BoardingProperties.class, LoggingOtpNotificationService.class})
class BoardingConcurrencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    private static final AtomicLong PHONE_SEQ = new AtomicLong(9_100_000_000L);

    @Autowired
    private RideRepository rideRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private BoardingTokenRepository boardingTokenRepository;
    @Autowired
    private BoardingVerificationRepository boardingVerificationRepository;

    @Autowired
    private BoardingServiceImpl boardingService;

    @Test
    void checkInWithToken_concurrentScansOfSameQr_exactlyOneSucceeds() throws Exception {
        User driver = persistUser();
        Vehicle vehicle = persistVehicle(driver);
        Ride ride = persistRide(driver, vehicle);
        User passenger = persistUser();
        Booking booking = persistAcceptedBooking(ride, passenger);
        BoardingToken token = persistActiveToken(booking, driver);

        int contenders = 8;
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<Boolean>> futures = IntStream.range(0, contenders)
                .mapToObj(i -> pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        boardingService.checkInWithToken(passenger.getId(), token.getToken());
                        return true;
                    } catch (ResponseStatusException e) {
                        return false;
                    }
                }))
                .toList();

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();

        int successes = 0;
        for (Future<Boolean> future : futures) {
            if (future.get(30, TimeUnit.SECONDS)) {
                successes++;
            }
        }
        pool.shutdown();

        assertThat(successes)
                .as("exactly one of %d concurrent scans of the same QR should win", contenders)
                .isEqualTo(1);

        Booking reloadedBooking = bookingRepository.findById(booking.getBookingId()).orElseThrow();
        assertThat(reloadedBooking.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);

        BoardingToken reloadedToken = boardingTokenRepository.findById(token.getTokenId()).orElseThrow();
        assertThat(reloadedToken.getConsumedAt()).isNotNull();

        List<BoardingVerification> verifications = boardingVerificationRepository.findAll();
        assertThat(verifications)
                .as("exactly one verification row for this booking, not one per racing attempt")
                .hasSize(1);
        assertThat(verifications.get(0).getCheckedInVia()).isEqualTo(CheckedInVia.QR);
        assertThat(verifications.get(0).getCheckedInAt()).isNotNull();
    }

    private User persistUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Test User");
        user.setCollegeEmail("user-" + UUID.randomUUID() + "@test.edu");
        user.setPhoneNumber(String.valueOf(PHONE_SEQ.incrementAndGet()));
        return userRepository.save(user);
    }

    private Vehicle persistVehicle(User owner) {
        Vehicle vehicle = new Vehicle();
        vehicle.setOwner(owner);
        vehicle.setNumberPlate("KA-" + UUID.randomUUID());
        vehicle.setCarMake("Honda");
        vehicle.setCarModel("Amaze");
        vehicle.setNoOfSeats(4);
        vehicle.setIsActive(true);
        vehicle.setIsVerified(true);
        return vehicleRepository.save(vehicle);
    }

    private Ride persistRide(User driver, Vehicle vehicle) {
        Ride ride = new Ride();
        ride.setDriver(driver);
        ride.setVehicle(vehicle);
        ride.setPickup(geoPoint(12.9716, 77.5946));
        ride.setDestination(geoPoint(13.1986, 77.7066));
        ride.setDepartureTime(OffsetDateTime.now().plusHours(2));
        ride.setOfferedSeats(4);
        ride.setTotalSeats(4);
        ride.setAvailableSeats(3);
        ride.setStatus(RideStatus.SCHEDULED);
        return rideRepository.save(ride);
    }

    private Booking persistAcceptedBooking(Ride ride, User passenger) {
        Booking booking = new Booking();
        booking.setRide(ride);
        booking.setPassenger(passenger);
        booking.setSeatsRequested(1);
        booking.setPickupLat(12.97);
        booking.setPickupLng(77.59);
        booking.setDropLat(13.02);
        booking.setDropLng(77.63);
        booking.setStatus(BookingStatus.ACCEPTED);
        return bookingRepository.save(booking);
    }

    private BoardingToken persistActiveToken(Booking booking, User driver) {
        BoardingToken token = new BoardingToken();
        token.setToken("race-token-" + UUID.randomUUID());
        token.setBooking(booking);
        token.setCreatedBy(driver);
        token.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
        return boardingTokenRepository.save(token);
    }

    private GeoPoint geoPoint(double lat, double lng) {
        GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);
        return new GeoPoint("Test Location", "123 Test Street", factory.createPoint(new Coordinate(lng, lat)));
    }
}
