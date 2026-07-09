package com.linkride.backend.discovery;

import com.linkride.backend.location.GeoPointDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Passenger-facing trip search payload — {@code POST /api/rides/search}.
 *
 * <p>{@code maxWalkToPickupMeters}/{@code maxWalkFromDropMeters} are optional overrides of the
 * system default walking tolerance. They're accepted and validated here but not yet used for
 * anything — that lands with corridor computation in Phase 2B.5.</p>
 */
@Data
public class TripSearchRequest {

    @NotNull(message = "Origin is required")
    @Valid
    private GeoPointDto origin;

    @NotNull(message = "Destination is required")
    @Valid
    private GeoPointDto destination;

    @NotNull(message = "Desired departure time is required")
    @Future(message = "Desired departure time must be in the future")
    private OffsetDateTime desiredDepartureTime;

    @NotNull(message = "Passenger count is required")
    @Min(value = 1, message = "Passenger count must be at least 1")
    private Integer passengerCount;

    @Min(value = 0, message = "Max walk to pickup must not be negative")
    private Integer maxWalkToPickupMeters;

    @Min(value = 0, message = "Max walk from drop must not be negative")
    private Integer maxWalkFromDropMeters;
}
