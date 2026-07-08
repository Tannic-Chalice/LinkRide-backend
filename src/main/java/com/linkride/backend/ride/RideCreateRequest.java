package com.linkride.backend.ride;

import com.linkride.backend.location.GeoPointDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Driver-facing ride creation payload. Deliberately has no {@code driverId},
 * {@code vehicleId}, {@code totalSeats}, route fields, {@code status}, or timestamps —
 * those are backend-resolved in {@link RideService}, never accepted from the client.
 */
@Data
public class RideCreateRequest {

    @NotNull(message = "Pickup is required")
    @Valid
    private GeoPointDto pickup;

    @NotNull(message = "Destination is required")
    @Valid
    private GeoPointDto destination;

    @NotNull(message = "Departure time is required")
    @Future(message = "Departure time must be in the future")
    private OffsetDateTime departureTime;

    @NotNull(message = "Offered seats is required")
    @Min(value = 1, message = "Offered seats must be at least 1")
    private Integer offeredSeats;

    @Valid
    private List<GeoPointDto> waypoints;

    @Valid
    private RepeatSchedule repeatSchedule;

    @Valid
    private RidePreferencesDto preferences;
}
