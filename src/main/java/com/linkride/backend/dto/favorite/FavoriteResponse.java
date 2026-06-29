package com.linkride.backend.dto.favorite;

import com.linkride.backend.enums.FavoriteIcon;
import com.linkride.backend.enums.FavoriteType;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Response payload for a single favourite location.
 * Returned by all favourite CRUD endpoints and embedded in {@link com.linkride.backend.dto.home.HomeResponse}.
 */
@Data
@Builder
public class FavoriteResponse {
    private UUID         favoriteId;
    private String       label;
    private FavoriteType type;
    private String       address;
    private Double       latitude;
    private Double       longitude;
    private FavoriteIcon icon;
    private Boolean      isPinned;
    /** 1-based display position. Client uses this field to render the list in order. */
    private Integer      displayOrder;
}
