package com.linkride.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Uniform error envelope returned by all API error responses.
 *
 * <ul>
 *   <li>{@code error}   — short machine-readable code, e.g. {@code "FAVORITE_NOT_FOUND"}</li>
 *   <li>{@code message} — human-readable description safe to display in logs / debug tools</li>
 * </ul>
 */
@Data
@AllArgsConstructor
public class ErrorResponse {
    private String error;
    private String message;
}
