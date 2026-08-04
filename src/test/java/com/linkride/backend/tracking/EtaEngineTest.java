package com.linkride.backend.tracking;

import com.linkride.backend.booking.Booking;
import com.linkride.backend.booking.BookingStatus;
import com.linkride.backend.route.geometry.BoundingBox;
import com.linkride.backend.route.geometry.GeoMath;
import com.linkride.backend.route.geometry.LatLng;
import com.linkride.backend.route.geometry.RouteGeometry;
import com.linkride.backend.route.geometry.RoutePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pure unit tests against synthetic {@link RouteGeometry}/{@link RouteProgress} fixtures — no
 * Spring context, same style as {@code RouteEtaCalculator}'s own math (§15).
 */
class EtaEngineTest {

    private static final double LAT = 12.9716;
    private static final double START_LNG = 77.5990;
    private static final double END_LNG = 77.6090;

    private final EtaEngine engine = new EtaEngine();

    @Test
    void etaTo_targetAheadOfDriver_returnsNowPlusProportionalRemainingTime() {
        RouteGeometry geometry = straightLineGeometry(1000);
        RouteProgress progress = progressAt(0); // driver at the very start
        double targetCumulativeDistanceMeters = geometry.totalDistanceMeters(); // the far end
        Instant now = Instant.now();

        Instant eta = engine.etaTo(geometry, progress, targetCumulativeDistanceMeters, now);

        assertThat(eta).isCloseTo(now.plusSeconds(1000), within(1, java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void etaTo_targetAlreadyPassed_clampsToNow() {
        RouteGeometry geometry = straightLineGeometry(1000);
        RouteProgress progress = progressAt(geometry.totalDistanceMeters()); // driver at the end
        Instant now = Instant.now();

        // Target is behind the driver -- remaining distance would be negative without clamping.
        Instant eta = engine.etaTo(geometry, progress, 0, now);

        assertThat(eta).isEqualTo(now);
    }

    @Test
    void etaForBooking_acceptedBooking_returnsEtaToPickup() {
        RouteGeometry geometry = straightLineGeometry(1000);
        RouteProgress progress = progressAt(0);
        Booking booking = booking(BookingStatus.ACCEPTED, LAT, END_LNG, LAT, END_LNG);
        Instant now = Instant.now();

        Optional<PassengerEtaView> view = engine.etaForBooking(geometry, progress, booking, now);

        assertThat(view).isPresent();
        assertThat(view.get().stop()).isEqualTo(StopType.PICKUP);
        assertThat(view.get().bookingId()).isEqualTo(booking.getBookingId());
        assertThat(view.get().arrivalState()).isEqualTo(ArrivalState.EN_ROUTE);
    }

    @Test
    void etaForBooking_checkedInBooking_returnsEtaToDropoff() {
        RouteGeometry geometry = straightLineGeometry(1000);
        RouteProgress progress = progressAt(0);
        Booking booking = booking(BookingStatus.CHECKED_IN, LAT, START_LNG, LAT, END_LNG);
        Instant now = Instant.now();

        Optional<PassengerEtaView> view = engine.etaForBooking(geometry, progress, booking, now);

        assertThat(view).isPresent();
        assertThat(view.get().stop()).isEqualTo(StopType.DROPOFF);
    }

    @Test
    void etaForBooking_pendingBooking_isExcluded() {
        RouteGeometry geometry = straightLineGeometry(1000);
        RouteProgress progress = progressAt(0);
        Booking booking = booking(BookingStatus.PENDING, LAT, START_LNG, LAT, END_LNG);

        assertThat(engine.etaForBooking(geometry, progress, booking, Instant.now())).isEmpty();
    }

    private RouteProgress progressAt(double driverCumulativeDistanceMeters) {
        return new RouteProgress(driverCumulativeDistanceMeters, 0, 0, 0, true, List.of());
    }

    private Booking booking(BookingStatus status, double pickupLat, double pickupLng, double dropLat, double dropLng) {
        Booking booking = new Booking();
        booking.setBookingId(UUID.randomUUID());
        booking.setStatus(status);
        booking.setPickupLat(pickupLat);
        booking.setPickupLng(pickupLng);
        booking.setDropLat(dropLat);
        booking.setDropLng(dropLng);
        return booking;
    }

    private RouteGeometry straightLineGeometry(double totalDurationSeconds) {
        double totalDistance = GeoMath.haversineMeters(LAT, START_LNG, LAT, END_LNG);
        List<RoutePoint> points = List.of(
                new RoutePoint(LAT, START_LNG, 0, 0, 0),
                new RoutePoint(LAT, END_LNG, totalDistance, totalDurationSeconds, 1));
        List<LatLng> latLngs = points.stream().map(p -> new LatLng(p.lat(), p.lng())).toList();
        return new RouteGeometry(points, totalDistance, totalDurationSeconds, BoundingBox.of(latLngs));
    }
}
