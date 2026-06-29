package com.linkride.backend.dto.favorite;

import com.linkride.backend.enums.FavoriteIcon;
import com.linkride.backend.enums.FavoriteType;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Request body for {@code POST /api/favorites} and {@code PUT /api/favorites/{id}}.
 *
 * <p>All fields are required except {@code isPinned}, which defaults to {@code false}
 * when omitted.</p>
 */
@Data
public class FavoriteRequest {

    @NotBlank(message = "Label is required")
    @Size(min = 1, max = 60, message = "Label must be between 1 and 60 characters")
    private String label;

    @NotNull(message = "Type is required")
    private FavoriteType type;

    @NotBlank(message = "Address is required")
    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0",  message = "Latitude must be >= -90")
    @DecimalMax(value = "90.0",   message = "Latitude must be <= 90")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
    @DecimalMax(value = "180.0",  message = "Longitude must be <= 180")
    private Double longitude;

    @NotNull(message = "Icon is required")
    private FavoriteIcon icon;

    // Defaults to false when the client omits the field
    private Boolean isPinned = false;
}
