package com.linkride.backend.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkride.backend.config.JwtService;
import com.linkride.backend.dto.ErrorResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private JwtService jwtService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtAuthFilter filter;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void invalidToken_writesStandardErrorEnvelopeWithCorrelationId() throws Exception {
        filter = new JwtAuthFilter(jwtService, objectMapper);
        MDC.put(CorrelationIdFilter.MDC_KEY, "trace-abc");

        when(request.getHeader("Authorization")).thenReturn("Bearer bad.token.here");
        when(request.getRequestURI()).thenReturn("/api/rides");
        when(jwtService.validateAndExtractClaims("bad.token.here"))
                .thenThrow(new JwtException("signature mismatch"));

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json");
        verifyNoInteractions(filterChain);

        ErrorResponse body = objectMapper.readValue(sw.toString(), ErrorResponse.class);
        assertThat(body.getCode()).isEqualTo("UNAUTHENTICATED");
        assertThat(body.getError()).isEqualTo("UNAUTHENTICATED");
        assertThat(body.getPath()).isEqualTo("/api/rides");
        assertThat(body.getCorrelationId()).isEqualTo("trace-abc");
        assertThat(body.getTimestamp()).isNotNull();
    }

    @Test
    void validToken_continuesTheChainWithoutWritingAResponse() throws Exception {
        filter = new JwtAuthFilter(jwtService, objectMapper);

        when(request.getHeader("Authorization")).thenReturn("Bearer good.token.here");
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn("11111111-1111-1111-1111-111111111111");
        when(jwtService.validateAndExtractClaims("good.token.here")).thenReturn(claims);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(response);
    }

    @Test
    void noAuthorizationHeader_passesThroughUntouched() throws Exception {
        filter = new JwtAuthFilter(jwtService, objectMapper);

        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(response);
        verifyNoInteractions(jwtService);
    }
}
