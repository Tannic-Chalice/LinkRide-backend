package com.linkride.backend.dto.home;

import lombok.Builder;
import lombok.Data;

/**
 * A pre-seeded popular destination shown on the Home Screen.
 *
 * <p>Currently backed by a static list in {@link com.linkride.backend.service.HomeService}.
 * When a content-management requirement arises, extract to a {@code popular_destinations}
 * DB table with a separate admin API.</p>
 */
@Data
@Builder
public class PopularDestinationDto {
    private String id;
    private String name;
    private String address;
}
