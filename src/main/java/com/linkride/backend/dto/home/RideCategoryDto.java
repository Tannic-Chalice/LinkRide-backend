package com.linkride.backend.dto.home;

import com.linkride.backend.enums.RideCategoryId;
import lombok.Builder;
import lombok.Data;

/**
 * A vehicle category shown on the Home Screen (e.g. Car, Bike, Auto).
 * The ride-booking module uses {@code id} to filter available vehicle types.
 */
@Data
@Builder
public class RideCategoryDto {
    private RideCategoryId id;
    private String         name;
    /** Icon key string consumed by the frontend icon resolver. */
    private String         icon;
}
