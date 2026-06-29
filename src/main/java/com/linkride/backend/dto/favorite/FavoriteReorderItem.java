package com.linkride.backend.dto.favorite;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * A single element within a {@code PATCH /api/favorites/reorder} request.
 * Maps a favourite to its new 1-based display position.
 */
@Data
public class FavoriteReorderItem {

    @NotNull(message = "favoriteId is required")
    private UUID favoriteId;

    @NotNull(message = "displayOrder is required")
    @Min(value = 1, message = "displayOrder must be >= 1")
    private Integer displayOrder;
}
