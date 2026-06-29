package com.linkride.backend.dto.home;

import lombok.Builder;
import lombok.Data;

/**
 * The device's current GPS position reflected back in the Home response.
 *
 * <p>The backend never stores this data — it is supplied by the client as
 * query parameters ({@code lat}, {@code lng}, {@code locationName}) and
 * echoed here so the UI has a single response object to bind from.</p>
 */
@Data
@Builder
public class CurrentLocationDto {
    private Double latitude;
    private Double longitude;
    /** Reverse-geocoded human-readable name supplied by the client (may be empty). */
    private String name;
}
