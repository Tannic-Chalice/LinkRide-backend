package com.linkride.backend.booking;

import com.linkride.backend.ride.Ride;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class BookingResponse {

    private UUID bookingId;
    private UUID rideId;
    private UUID passengerId;
    private Integer seatsRequested;

    private double pickupLat;
    private double pickupLng;
    private String pickupLabel;
    private double dropLat;
    private double dropLng;
    private String dropLabel;

    private BookingStatus status;

    private OffsetDateTime requestedAt;
    private OffsetDateTime decidedAt;
    private OffsetDateTime cancelledAt;
    private CancelInitiator cancelledBy;

    private BookingRideSummaryDto ride;

    public static BookingResponse from(Booking booking) {
        return BookingResponse.builder()
                .bookingId(booking.getBookingId())
                .rideId(booking.getRide().getRideId())
                .passengerId(booking.getPassenger().getId())
                .seatsRequested(booking.getSeatsRequested())
                .pickupLat(booking.getPickupLat())
                .pickupLng(booking.getPickupLng())
                .pickupLabel(booking.getPickupLabel())
                .dropLat(booking.getDropLat())
                .dropLng(booking.getDropLng())
                .dropLabel(booking.getDropLabel())
                .status(booking.getStatus())
                .requestedAt(booking.getCreatedAt())
                .decidedAt(booking.getDecidedAt())
                .cancelledAt(booking.getCancelledAt())
                .cancelledBy(booking.getCancelledBy())
                .ride(rideSummary(booking.getRide()))
                .build();
    }

    private static BookingRideSummaryDto rideSummary(Ride ride) {
        return BookingRideSummaryDto.builder()
                .rideId(ride.getRideId())
                .driverId(ride.getDriver().getId())
                .driverName(ride.getDriver().getFullName())
                .pickupName(ride.getPickup().getName())
                .destinationName(ride.getDestination().getName())
                .departureTime(ride.getDepartureTime())
                .status(ride.getStatus())
                .build();
    }
}
