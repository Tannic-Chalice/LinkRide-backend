package com.linkride.backend.dto.home;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Slim user profile embedded in {@link HomeResponse}.
 *
 * <p>{@code fullName} is returned as-is from {@code User.fullName} — no
 * string splitting is performed. If the UI ever needs separated first/last
 * names, the correct fix is adding dedicated columns to the {@code users}
 * table rather than parsing at the service layer.</p>
 */
@Data
@Builder
public class HomeUserDto {
    private UUID       id;
    private String     fullName;
    private BigDecimal rating;
}
