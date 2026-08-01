package com.linkride.backend.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Servlet filter that runs once per request to assign every request a correlation ID, so a
 * single failure can be traced from the {@code ErrorResponse} envelope back to the exact log
 * lines that produced it (Phase 5 §3 — see backend/docs/phase-5-platform-hardening.md).
 *
 * <p>Runs before {@link JwtAuthFilter} in the security chain so the ID is available to auth
 * failures too, and applies to every request the chain sees (public and authenticated alike).</p>
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "correlationId";

    // Client-supplied IDs are echoed back and logged verbatim, so only accept a conservative
    // charset/length instead of trusting the header outright (avoids header-injection/log-spam).
    private static final Pattern SAFE_ID = Pattern.compile("^[a-zA-Z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String correlationId = request.getHeader(HEADER_NAME);
        if (correlationId == null || !SAFE_ID.matcher(correlationId).matches()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Tomcat reuses worker threads across requests -- MDC must not leak between them.
            MDC.remove(MDC_KEY);
        }
    }
}
