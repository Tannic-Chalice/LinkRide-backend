package com.linkride.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkride.backend.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final RestAccessDeniedHandler handler = new RestAccessDeniedHandler(objectMapper);

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Test
    void handle_writesStandardEnvelopeWith403() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/rides/42");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        handler.handle(request, response, new AccessDeniedException("nope"));

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(response).setContentType("application/json");

        ErrorResponse body = objectMapper.readValue(sw.toString(), ErrorResponse.class);
        assertThat(body.getCode()).isEqualTo("ACCESS_DENIED");
        assertThat(body.getPath()).isEqualTo("/api/rides/42");
    }
}
