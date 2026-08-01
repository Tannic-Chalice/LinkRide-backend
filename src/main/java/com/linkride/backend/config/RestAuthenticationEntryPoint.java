package com.linkride.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkride.backend.filter.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Fires when Spring Security's own filter chain rejects a request before it ever reaches a
 * controller -- e.g. no {@code Authorization} header at all on a protected route, or a request
 * to an unmapped route (routing never happens; the chain denies it first). Without this, Spring
 * Security's default entry point returns its own generic JSON shape, inconsistent with every
 * other API error (Phase 5 §10 — see backend/docs/phase-5-platform-hardening.md).
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        ErrorResponseWriter.write(response, objectMapper, HttpStatus.UNAUTHORIZED.value(),
                "UNAUTHENTICATED", "Authentication is required", request);
    }
}
