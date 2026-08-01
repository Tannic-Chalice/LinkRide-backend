package com.linkride.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Uniform error envelope returned by all API error responses (Phase 5 §1/§9 — see
 * backend/docs/phase-5-platform-hardening.md).
 *
 * <ul>
 *   <li>{@code error}         — short machine-readable code, kept for backward compatibility
 *       with existing clients; identical to {@code code}</li>
 *   <li>{@code message}       — human-readable description safe to display in logs / debug tools</li>
 *   <li>{@code code}          — canonical machine-readable error code (see the registry in
 *       docs/api.md); new client code should branch on this, not {@code error}</li>
 *   <li>{@code path}          — the request URI that produced the error</li>
 *   <li>{@code timestamp}     — when the error was produced</li>
 *   <li>{@code correlationId} — matches the {@code X-Request-Id} response header and the
 *       server-side log lines for this request</li>
 *   <li>{@code details}       — per-field validation failures, when applicable</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String error;
    private String message;
    private String code;
    private String path;
    private OffsetDateTime timestamp;
    private String correlationId;
    private List<FieldViolation> details;

    /**
     * Legacy shape kept for the controllers that still build this envelope locally instead of
     * throwing through {@link com.linkride.backend.exception.GlobalExceptionHandler} (see
     * Phase 5 Final Review — a known, deliberately-deferred follow-up, not an oversight).
     */
    public ErrorResponse(String error, String message) {
        this.error = error;
        this.message = message;
        this.code = error;
        this.timestamp = OffsetDateTime.now();
    }

    @Data
    @AllArgsConstructor
    public static class FieldViolation {
        private String field;
        private String message;
    }
}
