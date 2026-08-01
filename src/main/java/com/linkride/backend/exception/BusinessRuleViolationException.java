package com.linkride.backend.exception;

import lombok.Getter;

/**
 * A request is well-formed but violates a service-level business rule (Phase 5 §10 — see
 * backend/docs/phase-5-platform-hardening.md), e.g. a cap reached or a duplicate singleton
 * resource. Carries its own machine-readable {@code code} so call sites that previously returned
 * distinct codes for the same HTTP status (e.g. {@code BUSINESS_RULE_VIOLATION} vs.
 * {@code INVALID_REORDER}) keep doing so through {@link GlobalExceptionHandler}, instead of
 * collapsing onto one generic code. Always maps to {@code 400}.
 */
@Getter
public class BusinessRuleViolationException extends RuntimeException {

    private final String code;

    public BusinessRuleViolationException(String code, String message) {
        super(message);
        this.code = code;
    }
}
