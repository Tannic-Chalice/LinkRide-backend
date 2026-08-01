package com.linkride.backend.exception;

import lombok.Getter;

/**
 * A requested resource doesn't exist, or exists but isn't owned by the caller (Phase 5 §10 —
 * see backend/docs/phase-5-platform-hardening.md). Carries its own machine-readable {@code code}
 * so call sites that previously returned distinct codes for the same HTTP status (e.g.
 * {@code USER_NOT_FOUND} vs. {@code FAVORITE_NOT_FOUND}) keep doing so through
 * {@link GlobalExceptionHandler}, instead of collapsing onto one generic code. Always maps to
 * {@code 404}.
 */
@Getter
public class ResourceNotFoundException extends RuntimeException {

    private final String code;

    public ResourceNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }
}
