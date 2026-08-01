package com.linkride.backend.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkride.backend.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;
import java.time.OffsetDateTime;

/**
 * Writes the shared {@link ErrorResponse} envelope directly to the servlet response, for the
 * handful of places that must respond before/outside normal Spring MVC dispatch -- JWT parsing
 * failures and Spring Security's own authentication-entry-point / access-denied handling
 * (Phase 5 §10 — see backend/docs/phase-5-platform-hardening.md). Keeps those responses on the
 * exact same shape {@link com.linkride.backend.exception.GlobalExceptionHandler} produces.
 */
public final class ErrorResponseWriter {

    private ErrorResponseWriter() {
    }

    public static void write(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            int status,
            String code,
            String message,
            HttpServletRequest request) throws IOException {

        ErrorResponse body = ErrorResponse.builder()
                .error(code)
                .code(code)
                .message(message)
                .path(request.getRequestURI())
                .timestamp(OffsetDateTime.now())
                .correlationId(MDC.get(CorrelationIdFilter.MDC_KEY))
                .build();

        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
