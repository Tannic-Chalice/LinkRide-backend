package com.linkride.backend.discovery;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A bare coordinate on a route — deliberately not {@link com.linkride.backend.location.GeoPointDto},
 * which implies a named place (it requires {@code name}/{@code address}). A pickup/drop point
 * derived from corridor computation (Phase 2B.5) is just where the driver's route happens to
 * pass closest to the passenger's path — it has no name until/unless it's reverse-geocoded,
 * which is out of scope here.
 *
 * <p>Range-validated (Phase 3) since it's now also bound as a request body field
 * ({@code BookingCreateRequest.pickup/drop}) — previously only ever server-constructed.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LatLngDto {

    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90")
    private double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180")
    private double longitude;
}
