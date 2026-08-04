package com.linkride.backend.config;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Validates the caller's Supabase JWT during the HTTP-&gt;WebSocket upgrade for
 * {@code /ws/tracking} (§11.2 of backend/docs/phase-7-live-trip-management.md) — before any
 * STOMP session exists. Reuses {@link JwtService}, the same JWKS-backed validation
 * {@link com.linkride.backend.filter.JwtAuthFilter} already performs for REST, rather than
 * duplicating it. A missing or invalid token fails the handshake outright, at the HTTP level,
 * before any socket is ever established.
 *
 * <p>Native mobile STOMP clients can set arbitrary headers on the handshake request (unlike a
 * browser's JS {@code WebSocket} API), so the same {@code Authorization: Bearer} header REST
 * uses works here too.</p>
 *
 * <p>On success, the resolved user UUID is stashed in the handshake {@code attributes} map under
 * {@link #PRINCIPAL_ATTRIBUTE} — {@link TrackingWebSocketConfig}'s handshake handler reads it
 * back to construct the STOMP session's {@link java.security.Principal}.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class StompHandshakeInterceptor implements HandshakeInterceptor {

    public static final String PRINCIPAL_ATTRIBUTE = "principal";

    private final JwtService jwtService;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            String userId = jwtService.extractUserId(authHeader.substring(7));
            if (userId == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put(PRINCIPAL_ATTRIBUTE, userId);
            return true;
        } catch (JwtException e) {
            log.warn("WebSocket handshake JWT validation failed: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // No cleanup needed regardless of handshake outcome.
    }
}
