package com.linkride.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkride.backend.filter.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Fires when Spring Security denies an already-identified principal -- not reachable today
 * (the app has no method-level authorization yet), but wired now so the response shape is
 * already correct the moment role-based checks (e.g. {@code @PreAuthorize}) are introduced
 * (Phase 5 §10 — see backend/docs/phase-5-platform-hardening.md).
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        ErrorResponseWriter.write(response, objectMapper, HttpStatus.FORBIDDEN.value(),
                "ACCESS_DENIED", "You do not have permission to perform this action", request);
    }
}
