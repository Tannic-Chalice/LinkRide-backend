package com.linkride.backend.tracking;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

/**
 * Driver's GPS ping ({@code POST /rides/{rideId}/location}). Coordinate bounds are the only
 * check declarative Bean Validation can express here (§7.1 step 1, same convention as
 * {@link com.linkride.backend.location.GeoPointDto}) — timestamp sanity, duplicate/out-of-order,
 * and impossible-jump detection all need the ride's previous {@link LiveRideState} and the live
 * server clock, so those run in {@link GpsFixValidator} instead (§7.1 steps 2-4).
 *
 * <p>Deliberately excludes client-reported speed/heading — nothing in this design consumes
 * them; implied speed for the jump check is always computed server-side from two consecutive
 * fixes (§7.1).</p>
 */
@Data
public class LocationUpdateRequest {

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180")
    private Double longitude;

    @NotNull(message = "recordedAt is required")
    private Instant recordedAt;
}
