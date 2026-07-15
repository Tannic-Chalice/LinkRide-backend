package com.linkride.backend.booking;

import com.linkride.backend.discovery.LatLngDto;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves what {@code BookingServiceImplTest}'s Mockito tests can't: real database guarantees that
 * only exist once actual constraints/locks are in play, not a mocked repository. Two things live
 * here: (1) that two real, concurrent transactions racing for the same ride's last seat cannot
 * both win — mocks can show the code path handles a {@code 0} from {@code reserveSeats} correctly,
 * but only a real Postgres can show it actually serializes the two {@code UPDATE}s the way the
 * design assumes; (2) that {@code uq_bookings_active_ride_passenger} (the V5 migration's partial
 * unique index) really does allow a re-request after a prior booking went terminal while really
 * blocking a second active one — a Mockito test can only assert what
 * {@code existsActiveBookingForRideAndPassenger} was stubbed to return, not what the index itself
 * enforces.
 *
 * <p>Deliberately not a {@code *IT} class — this project only runs Surefire (no Failsafe), so an
 * {@code *IT} name would silently never execute under {@code mvn test}. {@code disabledWithoutDocker}
 * makes it self-skip rather than fail the build on a machine without Docker, instead of making
 * Docker a hard requirement for the whole test suite.</p>
 *
 * <p>Runs against a throwaway schema, not the real Flyway migration chain: {@code V2} assumes
 * {@code users}/{@code vehicles} already exist, which in the real Supabase DB predate Flyway
 * (baselined as version 1, never captured in a checked-in migration) — a fresh Testcontainers
 * instance has no such baseline. {@code ddl-auto=create-drop} sidesteps that for this isolated
 * test database only; the real app still runs {@code validate} against Flyway-owned schema.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED) // setup must actually commit — see class javadoc
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Testcontainers(disabledWithoutDocker = true)
// @DataJpaTest's component scan is deliberately narrow (entities + repositories only) and won't
// pick up @Service beans on its own — this import registers the real BookingServiceImpl as a
// Spring-managed bean so its @Transactional actually gets AOP-woven. Without this, `new
// BookingServiceImpl(...)` looks like it works but every repository call silently runs in its own
// auto-committed mini-transaction instead of one shared transaction — the lazy Ride proxy's
// session is gone by the time acceptBooking tries to use it (LazyInitializationException).
@Import(BookingServiceImpl.class)
class BookingConcurrencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    private static final AtomicLong PHONE_SEQ = new AtomicLong(9_000_000_000L);

    @Autowired
    private RideRepository rideRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private BookingServiceImpl bookingService;

    @Test
    void acceptBooking_concurrentAcceptsForLastSeat_exactlyOneWins() throws Exception {
        User driver = persistUser();
        Vehicle vehicle = persistVehicle(driver);
        Ride ride = persistRide(driver, vehicle, 1); // only 1 seat available

        int contenders = 8;
        List<Booking> bookings = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            bookings.add(persistPendingBooking(ride, persistUser()));
        }

        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<Boolean>> futures = new ArrayList<>();
        for (Booking booking : bookings) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    bookingService.acceptBooking(driver.getId(), booking.getBookingId());
                    return true;
                } catch (ResponseStatusException e) {
                    return false;
                }
            }));
        }

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
                .as("exactly one of %d concurrent accepts for a single seat should win", contenders)
                .isEqualTo(1);

        Ride reloaded = rideRepository.findById(ride.getRideId()).orElseThrow();
        assertThat(reloaded.getAvailableSeats()).isZero();

        long acceptedCount = bookings.stream()
                .map(b -> bookingRepository.findById(b.getBookingId()).orElseThrow())
                .filter(b -> b.getStatus() == BookingStatus.ACCEPTED)
                .count();
        assertThat(acceptedCount).isEqualTo(1);
    }

    @Test
    void requestBooking_uniqueActiveIndex_blocksOneAllowsAnotherAfterRejection() {
        User driver = persistUser();
        Vehicle vehicle = persistVehicle(driver);
        Ride ride = persistRide(driver, vehicle, 4);
        User passenger = persistUser();

        // A REJECTED booking for this exact (ride, passenger) pair already exists — the partial
        // index only covers PENDING/ACCEPTED, so it must not block a fresh request.
        Booking rejected = persistPendingBooking(ride, passenger);
        rejected.setStatus(BookingStatus.REJECTED);
        bookingRepository.saveAndFlush(rejected);

        BookingResponse first = bookingService.requestBooking(passenger.getId(), ride.getRideId(), bookingRequest());
        assertThat(first.getStatus()).isEqualTo(BookingStatus.PENDING);

        // That new booking is itself PENDING now — a second active request for the same pair
        // must be blocked, proven against the real index rather than a stubbed existence check.
        assertThatThrownBy(() -> bookingService.requestBooking(passenger.getId(), ride.getRideId(), bookingRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already have an active request");
    }

    private BookingCreateRequest bookingRequest() {
        BookingCreateRequest request = new BookingCreateRequest();
        request.setSeatsRequested(1);
        request.setPickup(new LatLngDto(12.97, 77.59));
        request.setDrop(new LatLngDto(13.02, 77.63));
        return request;
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

    private Ride persistRide(User driver, Vehicle vehicle, int availableSeats) {
        Ride ride = new Ride();
        ride.setDriver(driver);
        ride.setVehicle(vehicle);
        ride.setPickup(geoPoint(12.9716, 77.5946));
        ride.setDestination(geoPoint(13.1986, 77.7066));
        ride.setDepartureTime(OffsetDateTime.now().plusHours(2));
        ride.setOfferedSeats(4);
        ride.setTotalSeats(4);
        ride.setAvailableSeats(availableSeats);
        ride.setStatus(RideStatus.SCHEDULED);
        return rideRepository.save(ride);
    }

    private Booking persistPendingBooking(Ride ride, User passenger) {
        Booking booking = new Booking();
        booking.setRide(ride);
        booking.setPassenger(passenger);
        booking.setSeatsRequested(1);
        booking.setPickupLat(12.97);
        booking.setPickupLng(77.59);
        booking.setDropLat(13.02);
        booking.setDropLng(77.63);
        booking.setStatus(BookingStatus.PENDING);
        return bookingRepository.save(booking);
    }

    private GeoPoint geoPoint(double lat, double lng) {
        GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);
        return new GeoPoint("Test Location", "123 Test Street", factory.createPoint(new Coordinate(lng, lat)));
    }
}
