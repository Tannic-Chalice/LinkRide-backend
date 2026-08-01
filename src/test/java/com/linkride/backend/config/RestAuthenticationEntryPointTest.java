package com.linkride.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkride.backend.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestAuthenticationEntryPointTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper);

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Test
    void commence_writesStandardEnvelopeWith401() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/rides");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        entryPoint.commence(request, response, new BadCredentialsException("no auth"));

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json");

        ErrorResponse body = objectMapper.readValue(sw.toString(), ErrorResponse.class);
        assertThat(body.getCode()).isEqualTo("UNAUTHENTICATED");
        assertThat(body.getPath()).isEqualTo("/api/rides");
    }
}
