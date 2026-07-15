package com.linkride.backend.booking;

import com.linkride.backend.ride.RideStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Just enough ride context for a booking list view to be useful without a second round trip —
 * not the full {@code RideResponse} (no route/waypoints/preferences, none of which a passenger
 * scanning their own booking list needs).
 */
@Data
@Builder
public class BookingRideSummaryDto {
    private UUID rideId;
    private UUID driverId;
    private String driverName;
    private String pickupName;
    private String destinationName;
    private OffsetDateTime departureTime;
    private RideStatus status;
}
