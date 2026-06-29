package com.linkride.backend.dto.home;

import lombok.Builder;
import lombok.Data;

/**
 * Client-side configuration values shipped with the Home response.
 *
 * <p>Allows the frontend to enforce business rules (e.g. favourite cap,
 * feature flags) without hardcoding them in the app.</p>
 */
@Data
@Builder
public class HomeConfigDto {
    /** Hard cap on the number of favourites a user may save. */
    private int     maxFavorites;
    /** Feature flag — when {@code false} the UI should hide the custom-place option. */
    private boolean allowCustomFavorites;
}
