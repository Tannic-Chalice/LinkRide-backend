package com.linkride.backend.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void noRequestId_generatesOneAndEchoesItBack() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        ArgumentCaptor<String> headerValue = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq(CorrelationIdFilter.HEADER_NAME), headerValue.capture());
        assertThat(headerValue.getValue()).isNotBlank();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void validClientSuppliedId_isEchoedBackUnchanged() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn("client-supplied-id-123");

        filter.doFilter(request, response, filterChain);

        verify(response).setHeader(CorrelationIdFilter.HEADER_NAME, "client-supplied-id-123");
    }

    @Test
    void malformedClientSuppliedId_isReplacedWithGeneratedId() throws Exception {
        // Contains a CR/LF-style control sequence disguised as a header-injection attempt.
        when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn("bad\r\nX-Injected: true");

        filter.doFilter(request, response, filterChain);

        ArgumentCaptor<String> headerValue = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq(CorrelationIdFilter.HEADER_NAME), headerValue.capture());
        assertThat(headerValue.getValue()).doesNotContain("\r", "\n").isNotEqualTo("bad\r\nX-Injected: true");
    }

    @Test
    void mdcIsClearedAfterTheRequestCompletes() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn("trace-me");

        filter.doFilter(request, response, filterChain);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void mdcIsPopulatedWhileTheChainRuns() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn("trace-me");

        doAnswerCaptureMdcDuringChain();

        filter.doFilter(request, response, filterChain);
    }

    private void doAnswerCaptureMdcDuringChain() throws Exception {
        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("trace-me");
            return null;
        }).when(filterChain).doFilter(request, response);
    }
}
