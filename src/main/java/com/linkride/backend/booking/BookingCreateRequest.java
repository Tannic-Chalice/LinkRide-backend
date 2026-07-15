package com.linkride.backend.booking;

import com.linkride.backend.discovery.LatLngDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Passenger-facing input for {@code POST /api/rides/{rideId}/bookings}. {@code rideId} and
 * {@code passengerId} are deliberately absent — resolved server-side from the path and the JWT,
 * never trusted from the body (mirrors {@code RideCreateRequest}).
 */
@Data
public class BookingCreateRequest {

    @Min(value = 1, message = "seatsRequested must be at least 1")
    private Integer seatsRequested = 1;

    @NotNull(message = "Pickup location is required")
    @Valid
    private LatLngDto pickup;

    @Size(max = 255, message = "Pickup label must not exceed 255 characters")
    private String pickupLabel;

    @NotNull(message = "Drop location is required")
    @Valid
    private LatLngDto drop;

    @Size(max = 255, message = "Drop label must not exceed 255 characters")
    private String dropLabel;
}
