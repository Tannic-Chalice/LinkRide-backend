package com.linkride.backend.notification;

import com.linkride.backend.booking.Booking;
import com.linkride.backend.booking.BookingRepository;
import com.linkride.backend.booking.BookingServiceImpl;
import com.linkride.backend.booking.BookingStatus;
import com.linkride.backend.entity.User;
import com.linkride.backend.entity.Vehicle;
import com.linkride.backend.location.GeoPoint;
import com.linkride.backend.repository.UserRepository;
import com.linkride.backend.repository.VehicleRepository;
import com.linkride.backend.ride.Ride;
import com.linkride.backend.ride.RideRepository;
import com.linkride.backend.ride.RideStatus;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the two guarantees a Mockito unit test can't: (1) a transaction that publishes a {@link
 * NotificationEvent} and then rolls back produces zero {@link Notification} rows -- the single
 * most important correctness property in Phase 6 (architecture doc §6/§14/§16), resting entirely
 * on {@code @TransactionalEventListener(phase = AFTER_COMMIT)}; (2) a real producer call
 * ({@code BookingServiceImpl.acceptBooking}) really does result in a persisted notification once
 * its transaction commits, wired exactly as it runs in production (real constructor injection,
 * real {@link org.springframework.context.ApplicationEventPublisher}, real async listener).
 *
 * <p>{@code @Transactional(propagation = NOT_SUPPORTED)} at the class level, same reasoning as
 * {@code BookingConcurrencyTest}: the service calls under test must actually commit for {@code
 * AFTER_COMMIT} to ever fire, which a {@code @DataJpaTest}'s default per-test rollback would
 * silently prevent.
 *
 * <p>The notification executor is overridden with a {@link SimpleAsyncTaskExecutor} (a real,
 * separate thread per task — never a same-thread executor like Spring's {@code SyncTaskExecutor})
 * deliberately: {@code @TransactionalEventListener(AFTER_COMMIT)}'s callback runs while the
 * just-committed transaction's synchronization resources may still be bound to the calling
 * thread, and a nested {@code @Transactional} call sharing that thread can silently fail to
 * actually flush to the database — the exact bug this test caught during development, where a
 * same-thread executor made the write vanish with no exception at all. Production already avoids
 * this by running on a real thread pool; the positive-path assertion below polls briefly to
 * tolerate the small, genuine cross-thread race that comes with testing that real behavior
 * faithfully instead of masking it.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Testcontainers(disabledWithoutDocker = true)
@Import({
        BookingServiceImpl.class,
        NotificationServiceImpl.class,
        DeviceTokenServiceImpl.class,
        NotificationEventListener.class,
        NotificationEventIntegrationTest.NotificationEventProbe.class,
        NotificationEventIntegrationTest.TestSupportConfig.class
})
class NotificationEventIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private RideRepository rideRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private BookingServiceImpl bookingService;
    @Autowired
    private NotificationEventProbe probe;

    @Test
    void publishThenRollback_producesNoNotification() {
        UUID recipientId = UUID.randomUUID();
        NotificationEvent event = new NotificationEvent(
                recipientId, NotificationCategory.SYSTEM, "TEST_EVENT", "title", "body", null, null);

        assertThatThrownBy(() -> probe.publishThenRollback(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("forced rollback for test");

        assertThat(notificationRepository.findAll())
                .as("a rolled-back transaction must never produce a notification, since AFTER_COMMIT never fires")
                .noneMatch(n -> n.getRecipientUserId().equals(recipientId));
    }

    @Test
    void acceptBooking_realProducerCall_createsNotificationAfterCommit() {
        User driver = persistUser();
        Vehicle vehicle = persistVehicle(driver);
        Ride ride = persistRide(driver, vehicle);
        User passenger = persistUser();
        Booking booking = persistPendingBooking(ride, passenger);

        bookingService.acceptBooking(driver.getId(), booking.getBookingId());

        // The listener genuinely runs on a separate thread (see class javadoc) -- poll briefly
        // rather than asserting immediately, the same tolerance any real async consumer needs.
        awaitNotificationFor(passenger.getId(), "BOOKING_ACCEPTED");
    }

    private void awaitNotificationFor(UUID recipientId, String type) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            boolean found = notificationRepository.findAll().stream().anyMatch(n ->
                    n.getRecipientUserId().equals(recipientId)
                            && n.getType().equals(type)
                            && n.getCategory() == NotificationCategory.BOOKING
                            && n.getDeliveryStatus() == DeliveryStatus.SKIPPED); // no device token registered
            if (found) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        assertThat(notificationRepository.findAll())
                .as("acceptBooking's committed transaction should have delivered a %s notification", type)
                .anyMatch(n -> n.getRecipientUserId().equals(recipientId) && n.getType().equals(type));
    }

    private User persistUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Test User");
        user.setCollegeEmail("user-" + UUID.randomUUID() + "@test.edu");
        user.setPhoneNumber(String.valueOf(System.nanoTime()));
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
        ride.setAvailableSeats(4);
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

    /** Test-only: none of Phase 6's real producer call sites publish an event and then let the
     * same transaction fail -- every real one publishes as the last statement after a successful
     * flush. This exists purely to exercise the AFTER_COMMIT guarantee directly. */
    @Component
    @RequiredArgsConstructor
    static class NotificationEventProbe {
        private final ApplicationEventPublisher publisher;

        @Transactional
        void publishThenRollback(NotificationEvent event) {
            publisher.publishEvent(event);
            throw new RuntimeException("forced rollback for test");
        }
    }

    @org.springframework.boot.test.context.TestConfiguration
    @EnableAsync
    static class TestSupportConfig {

        @Bean("notificationExecutor")
        public Executor notificationExecutor() {
            return new SimpleAsyncTaskExecutor();
        }

        @Bean
        public NotificationProperties notificationProperties() {
            return new NotificationProperties();
        }

        @Bean
        public FcmPushSender fcmPushSender() {
            return (fcmToken, title, body, data) -> FcmPushSender.PushOutcome.SENT;
        }
    }
}
