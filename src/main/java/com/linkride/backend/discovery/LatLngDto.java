package com.linkride.backend.discovery;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A bare coordinate on a route — deliberately not {@link com.linkride.backend.location.GeoPointDto},
 * which implies a named place (it requires {@code name}/{@code address}). A pickup/drop point
 * derived from corridor computation (Phase 2B.5) is just where the driver's route happens to
 * pass closest to the passenger's path — it has no name until/unless it's reverse-geocoded,
 * which is out of scope here.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LatLngDto {
    private double latitude;
    private double longitude;
}
