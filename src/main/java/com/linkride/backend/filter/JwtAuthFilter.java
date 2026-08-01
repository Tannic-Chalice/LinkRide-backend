package com.linkride.backend.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkride.backend.config.JwtService;
import com.linkride.backend.dto.ErrorResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Servlet filter that runs once per request to authenticate Supabase JWTs.
 *
 * <p>Flow:</p>
 * <ol>
 *   <li>Read the {@code Authorization: Bearer <token>} header.</li>
 *   <li>Delegate signature + expiry verification to {@link JwtService}.</li>
 *   <li>Extract the Supabase user UUID from the JWT "sub" claim.</li>
 *   <li>Construct a Spring Security {@link UsernamePasswordAuthenticationToken}
 *       with the UUID string as the principal and place it in the
 *       {@link SecurityContextHolder} — making it available everywhere in the
 *       request lifecycle, including inside service beans.</li>
 * </ol>
 *
 * <p>If the token is absent, malformed, expired, or has a bad signature, the filter
 * sends a {@code 401 Unauthorized} response using the same {@link ErrorResponse} envelope as
 * every other API error (Phase 5 §10 — see backend/docs/phase-5-platform-hardening.md), so a
 * client can branch on {@code code} the same way whether the failure came from a controller or
 * from auth. {@link CorrelationIdFilter} runs before this one, so the correlation ID is already
 * in MDC and gets included here too.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Pass through if no Bearer token is present
        // (SecurityConfig will reject protected endpoints further down)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7); // strip "Bearer "

        try {
            Claims claims = jwtService.validateAndExtractClaims(jwt);
            String userId = claims.getSubject(); // Supabase stores the user UUID in "sub"

            // Only set authentication if not already set by an earlier filter
            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // principal = userId string; credentials = null; authorities = empty (stateless app)
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                Collections.emptyList()
                        );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

            filterChain.doFilter(request, response);

        } catch (JwtException ex) {
            log.warn("JWT validation failed for request [{}]: {}", request.getRequestURI(), ex.getMessage());
            ErrorResponseWriter.write(response, objectMapper, HttpStatus.UNAUTHORIZED.value(),
                    "UNAUTHENTICATED", "Invalid or expired token", request);
        }
    }
}
