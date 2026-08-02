package com.linkride.backend.config;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * §11.2 — the handshake is rejected at the HTTP level, before any STOMP session exists, for a
 * missing/malformed/invalid token. Uses real {@code MockHttpServletRequest}/-{@code Response}
 * (from {@code spring-test}) wrapped in the servlet adapters rather than mocking the
 * {@code ServerHttpRequest}/{@code ServerHttpResponse} interfaces directly.
 */
@ExtendWith(MockitoExtension.class)
class StompHandshakeInterceptorTest {

    @Mock
    private JwtService jwtService;

    private StompHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompHandshakeInterceptor(jwtService);
    }

    @Test
    void beforeHandshake_missingAuthorizationHeader_rejectsWithUnauthorized() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(
                request(null), response(servletResponse), null, attributes);

        assertThat(result).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(attributes).isEmpty();
    }

    @Test
    void beforeHandshake_nonBearerAuthorizationHeader_rejectsWithUnauthorized() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(
                request("Basic dXNlcjpwYXNz"), response(servletResponse), null, attributes);

        assertThat(result).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void beforeHandshake_invalidToken_rejectsWithUnauthorized() {
        when(jwtService.extractUserId("bad-token")).thenThrow(new JwtException("bad signature"));
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(
                request("Bearer bad-token"), response(servletResponse), null, attributes);

        assertThat(result).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(attributes).isEmpty();
    }

    @Test
    void beforeHandshake_validToken_resolvesPrincipalAttributeAndAllowsTheUpgrade() {
        String userId = UUID.randomUUID().toString();
        when(jwtService.extractUserId("valid-token")).thenReturn(userId);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(
                request("Bearer valid-token"), response(servletResponse), null, attributes);

        assertThat(result).isTrue();
        assertThat(attributes.get(StompHandshakeInterceptor.PRINCIPAL_ATTRIBUTE)).isEqualTo(userId);
    }

    private ServletServerHttpRequest request(String authorizationHeader) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        if (authorizationHeader != null) {
            servletRequest.addHeader("Authorization", authorizationHeader);
        }
        return new ServletServerHttpRequest(servletRequest);
    }

    private ServletServerHttpResponse response(MockHttpServletResponse servletResponse) {
        return new ServletServerHttpResponse(servletResponse);
    }
}
