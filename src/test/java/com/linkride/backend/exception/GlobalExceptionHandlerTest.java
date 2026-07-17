package com.linkride.backend.exception;

import com.linkride.backend.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidation_fieldError_returns400NamingTheInvalidField() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "otpVerifyRequest");
        bindingResult.addError(new FieldError("otpVerifyRequest", "otp", "OTP must be 6 digits"));
        MethodParameter parameter = new MethodParameter(getClass().getDeclaredMethod("dummy", String.class), 0);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("otp: OTP must be 6 digits");
    }

    @Test
    void handleIllegalArgument_returns400() {
        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(new IllegalArgumentException("bad input"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("bad input");
    }

    @Test
    void handleResponseStatus_preservesStatusAndReason() {
        ResponseStatusException e = new ResponseStatusException(HttpStatus.CONFLICT, "already taken");

        ResponseEntity<ErrorResponse> response = handler.handleResponseStatus(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("already taken");
    }

    @Test
    void handleUnexpected_returns500WithGenericMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getError()).isEqualTo("INTERNAL_ERROR");
    }

    @SuppressWarnings("unused")
    private void dummy(String otp) {
    }
}
