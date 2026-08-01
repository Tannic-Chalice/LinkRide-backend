package com.linkride.backend.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkride.backend.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthRateLimitFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Test
    void nonAuthRoute_passesThroughUntouchedRegardlessOfLimit() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(objectMapper, 1, 1, 60);
        when(request.getRequestURI()).thenReturn("/api/v1/rides");

        filter.doFilter(request, response, filterChain);
        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(2)).doFilter(request, response);
        verifyNoInteractions(response);
    }

    @Test
    void withinCapacity_passesThrough() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(objectMapper, 3, 3, 60);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getRemoteAddr()).thenReturn("10.0.0.2");

        filter.doFilter(request, response, filterChain);
        filter.doFilter(request, response, filterChain);
        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(3)).doFilter(request, response);
        verifyNoInteractions(response);
    }

    @Test
    void exceedingCapacity_returns429WithStandardEnvelopeAndRetryAfter() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(objectMapper, 1, 1, 42);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/signup");
        when(request.getRemoteAddr()).thenReturn("10.0.0.3");

        StringWriter sw = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilter(request, response, filterChain); // consumes the only token
        filter.doFilter(request, response, filterChain); // exceeds

        verify(filterChain, times(1)).doFilter(request, response);
        verify(response).setStatus(429);
        verify(response).setContentType("application/json");
        verify(response).setHeader("Retry-After", "42");

        ErrorResponse body = objectMapper.readValue(sw.toString(), ErrorResponse.class);
        assertThat(body.getCode()).isEqualTo("RATE_LIMITED");
        assertThat(body.getPath()).isEqualTo("/api/v1/auth/signup");
    }

    @Test
    void differentIps_haveIndependentBuckets() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(objectMapper, 1, 1, 60);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getRemoteAddr()).thenReturn("10.0.0.4", "10.0.0.5");

        filter.doFilter(request, response, filterChain);
        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(2)).doFilter(request, response);
        verifyNoInteractions(response);
    }
}
