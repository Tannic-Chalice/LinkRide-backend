package com.linkride.backend.tracking;

import com.linkride.backend.booking.Booking;
import com.linkride.backend.booking.BookingStatus;
import com.linkride.backend.entity.User;
import com.linkride.backend.notification.NotificationCategory;
import com.linkride.backend.notification.NotificationEvent;
import com.linkride.backend.route.geometry.BoundingBox;
import com.linkride.backend.route.geometry.LatLng;
import com.linkride.backend.route.geometry.RouteGeometry;
import com.linkride.backend.route.geometry.RoutePoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * State-machine unit tests (§15): monotonicity, no duplicate firing once ARRIVED, correct
 * type/category selection per stop. Uses a real {@link EtaEngine} (pure logic, no mocking
 * needed) against a synthetic straight-line {@link RouteGeometry}.
 */
@ExtendWith(MockitoExtension.class)
class ArrivalDetectionServiceTest {

    private static final double LAT = 12.9716;
    private static final double START_LNG = 77.5990;
    private static final double END_LNG = 77.6090; // ~1113m east of START_LNG

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ArrivalDetectionService service;
    private RouteGeometry geometry;
    private TrackingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new TrackingProperties(); // approaching=500m, arrived=50m
        service = new ArrivalDetectionService(new EtaEngine(), properties, eventPublisher);

        double totalDistance = com.linkride.backend.route.geometry.GeoMath.haversineMeters(LAT, START_LNG, LAT, END_LNG);
        List<RoutePoint> points = List.of(
                new RoutePoint(LAT, START_LNG, 0, 0, 0),
                new RoutePoint(LAT, END_LNG, totalDistance, 1000, 1));
        geometry = new RouteGeometry(points, totalDistance, 1000,
                BoundingBox.of(List.of(new LatLng(LAT, START_LNG), new LatLng(LAT, END_LNG))));
    }

    private Booking acceptedBooking(UUID bookingId, UUID passengerId, double pickupLat, double pickupLng) {
        User passenger = new User();
        passenger.setId(passengerId);
        Booking booking = new Booking();
        booking.setBookingId(bookingId);
        booking.setPassenger(passenger);
        booking.setStatus(BookingStatus.ACCEPTED);
        booking.setPickupLat(pickupLat);
        booking.setPickupLng(pickupLng);
        booking.setDropLat(LAT);
        booking.setDropLng(END_LNG);
        return booking;
    }

    private RouteProgress progressAt(double driverCumulativeDistanceMeters) {
        return new RouteProgress(driverCumulativeDistanceMeters, 0, 0, 0, true, List.of());
    }

    private LiveRideState freshState() {
        return new LiveRideState(UUID.randomUUID(), LAT, START_LNG, java.time.Instant.now(), new HashMap<>());
    }

    @Test
    void evaluate_farFromPickup_staysEnRouteAndDoesNotNotify() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = acceptedBooking(bookingId, UUID.randomUUID(), LAT, END_LNG); // pickup at the far end
        LiveRideState state = freshState();

        Map<UUID, ArrivalState> result = service.evaluate(geometry, progressAt(0), List.of(booking), state);

        assertThat(result.get(bookingId)).isEqualTo(ArrivalState.EN_ROUTE);
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void evaluate_withinApproachingRadius_transitionsAndNotifies() {
        UUID bookingId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        Booking booking = acceptedBooking(bookingId, passengerId, LAT, END_LNG); // pickup at the far end (cumulative ~totalDistance)
        LiveRideState state = freshState();

        // Driver is 300m short of the pickup -- inside the default 500m approaching radius.
        double driverCumulative = geometry.totalDistanceMeters() - 300;
        Map<UUID, ArrivalState> result = service.evaluate(geometry, progressAt(driverCumulative), List.of(booking), state);

        assertThat(result.get(bookingId)).isEqualTo(ArrivalState.APPROACHING);
        assertThat(state.getArrivalHighWaterMark().get(bookingId).get(StopType.PICKUP)).isEqualTo(ArrivalState.APPROACHING);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        NotificationEvent event = captor.getValue();
        assertThat(event.recipientUserId()).isEqualTo(passengerId);
        assertThat(event.category()).isEqualTo(NotificationCategory.RIDE);
        assertThat(event.type()).isEqualTo("RIDE_DRIVER_APPROACHING_PICKUP");
    }

    @Test
    void evaluate_withinArrivedRadius_firesArrivedNotification() {
        UUID bookingId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        Booking booking = acceptedBooking(bookingId, passengerId, LAT, END_LNG);
        LiveRideState state = freshState();

        double driverCumulative = geometry.totalDistanceMeters() - 10; // well within the default 50m arrived radius
        Map<UUID, ArrivalState> result = service.evaluate(geometry, progressAt(driverCumulative), List.of(booking), state);

        assertThat(result.get(bookingId)).isEqualTo(ArrivalState.ARRIVED);
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo("RIDE_DRIVER_ARRIVED_PICKUP");
    }

    @Test
    void evaluate_alreadyArrived_neverRegressesAndNeverRefires() {
        UUID bookingId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        Booking booking = acceptedBooking(bookingId, passengerId, LAT, END_LNG);
        LiveRideState state = freshState();
        EnumMap<StopType, ArrivalState> highWaterMark = new EnumMap<>(StopType.class);
        highWaterMark.put(StopType.PICKUP, ArrivalState.ARRIVED);
        state.getArrivalHighWaterMark().put(bookingId, highWaterMark);

        // A jittery fix now reports the driver much farther away than before.
        Map<UUID, ArrivalState> result = service.evaluate(geometry, progressAt(0), List.of(booking), state);

        assertThat(result.get(bookingId)).isEqualTo(ArrivalState.ARRIVED);
        assertThat(state.getArrivalHighWaterMark().get(bookingId).get(StopType.PICKUP)).isEqualTo(ArrivalState.ARRIVED);
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void evaluate_alreadyApproaching_repeatedPingsWithinSameRadius_doNotRefire() {
        UUID bookingId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        Booking booking = acceptedBooking(bookingId, passengerId, LAT, END_LNG);
        LiveRideState state = freshState();
        double driverCumulative = geometry.totalDistanceMeters() - 300;

        service.evaluate(geometry, progressAt(driverCumulative), List.of(booking), state);
        service.evaluate(geometry, progressAt(driverCumulative), List.of(booking), state);

        verify(eventPublisher, times(1)).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void evaluate_checkedInBooking_evaluatesDropoffNotPickup() {
        UUID bookingId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        User passenger = new User();
        passenger.setId(passengerId);
        Booking booking = new Booking();
        booking.setBookingId(bookingId);
        booking.setPassenger(passenger);
        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.setPickupLat(LAT);
        booking.setPickupLng(START_LNG);
        booking.setDropLat(LAT);
        booking.setDropLng(END_LNG);
        LiveRideState state = freshState();

        double driverCumulative = geometry.totalDistanceMeters() - 10;
        Map<UUID, ArrivalState> result = service.evaluate(geometry, progressAt(driverCumulative), List.of(booking), state);

        assertThat(result.get(bookingId)).isEqualTo(ArrivalState.ARRIVED);
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo("RIDE_DRIVER_ARRIVED_DROPOFF");
    }

    @Test
    void evaluate_otherBookingStatus_isSkippedEntirely() {
        Booking booking = new Booking();
        booking.setBookingId(UUID.randomUUID());
        booking.setStatus(BookingStatus.PENDING);
        LiveRideState state = freshState();

        Map<UUID, ArrivalState> result = service.evaluate(geometry, progressAt(0), List.of(booking), state);

        assertThat(result).isEmpty();
        assertThat(state.getArrivalHighWaterMark()).isEmpty();
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }
}
