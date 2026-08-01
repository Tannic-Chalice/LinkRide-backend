package com.linkride.backend.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.linkride.backend.dto.ErrorResponse;
import com.linkride.backend.filter.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Mock
    private HttpServletRequest request;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        lenient().when(request.getRequestURI()).thenReturn("/api/test");

        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logger.detachAppender(logAppender);
        MDC.clear();
    }

    @Test
    void handleValidation_multipleFieldErrors_returns400WithAllDetails() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "otpVerifyRequest");
        bindingResult.addError(new FieldError("otpVerifyRequest", "otp", "OTP must be 6 digits"));
        bindingResult.addError(new FieldError("otpVerifyRequest", "bookingId", "must not be null"));
        MethodParameter parameter = new MethodParameter(getClass().getDeclaredMethod("dummy", String.class), 0);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getPath()).isEqualTo("/api/test");
        assertThat(response.getBody().getDetails()).hasSize(2);
    }

    @Test
    void handleIllegalArgument_returns400() {
        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(new IllegalArgumentException("bad input"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("bad input");
    }

    @Test
    void handleConstraintViolation_returns400WithDetails() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = org.mockito.Mockito.mock(ConstraintViolation.class);
        jakarta.validation.Path path = org.mockito.Mockito.mock(jakarta.validation.Path.class);
        when(path.toString()).thenReturn("search.origin");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must not be null");
        ConstraintViolationException exception = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getDetails()).hasSize(1);
        assertThat(response.getBody().getDetails().get(0).getField()).isEqualTo("search.origin");
    }

    @Test
    void handleMalformedBody_returns400() {
        HttpMessageNotReadableException exception =
                new HttpMessageNotReadableException("bad json", (org.springframework.http.HttpInputMessage) null);

        ResponseEntity<ErrorResponse> response = handler.handleMalformedBody(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("MALFORMED_REQUEST_BODY");
    }

    @Test
    void handleTypeMismatch_returns400NamingTheParameter() throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(getClass().getDeclaredMethod("dummy", String.class), 0);
        MethodArgumentTypeMismatchException exception =
                new MethodArgumentTypeMismatchException("abc", Integer.class, "favoriteId", parameter, new NumberFormatException());

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("INVALID_PARAMETER");
        assertThat(response.getBody().getMessage()).contains("favoriteId");
    }

    @Test
    void handleMissingParameter_returns400NamingTheParameter() {
        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException("lat", "Double");

        ResponseEntity<ErrorResponse> response = handler.handleMissingParameter(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("INVALID_PARAMETER");
        assertThat(response.getBody().getMessage()).isEqualTo("Required parameter 'lat' is missing");
    }

    @Test
    void handleNoHandlerFound_returns404() {
        NoHandlerFoundException exception = new NoHandlerFoundException("GET", "/api/nope", null);

        ResponseEntity<ErrorResponse> response = handler.handleNoHandlerFound(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getCode()).isEqualTo("ROUTE_NOT_FOUND");
    }

    @Test
    void handleAccessDenied_returns403() {
        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(new AccessDeniedException("nope"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getCode()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void handleAuthentication_returns401() {
        ResponseEntity<ErrorResponse> response =
                handler.handleAuthentication(new BadCredentialsException("bad creds"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getCode()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void handleDataIntegrityViolation_returns409() {
        ResponseEntity<ErrorResponse> response =
                handler.handleDataIntegrityViolation(new DataIntegrityViolationException("unique constraint"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo("DATA_CONFLICT");
        assertThat(response.getBody().getMessage()).doesNotContain("unique constraint");
    }

    @Test
    void handleResourceNotFound_returns404WithTheExceptionsOwnCode() {
        ResourceNotFoundException e = new ResourceNotFoundException("FAVORITE_NOT_FOUND", "Favorite not found or access denied");

        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFound(e, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getCode()).isEqualTo("FAVORITE_NOT_FOUND");
        assertThat(response.getBody().getMessage()).isEqualTo("Favorite not found or access denied");
    }

    @Test
    void handleBusinessRuleViolation_returns400WithTheExceptionsOwnCode() {
        BusinessRuleViolationException e = new BusinessRuleViolationException("INVALID_REORDER", "orders must be contiguous");

        ResponseEntity<ErrorResponse> response = handler.handleBusinessRuleViolation(e, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("INVALID_REORDER");
        assertThat(response.getBody().getMessage()).isEqualTo("orders must be contiguous");
    }

    @Test
    void handleResponseStatus_preservesStatusAndReason() {
        ResponseStatusException e = new ResponseStatusException(HttpStatus.CONFLICT, "already taken");

        ResponseEntity<ErrorResponse> response = handler.handleResponseStatus(e, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("already taken");
    }

    @Test
    void handleUnexpected_returns500WithGenericMessage_andLogsTheStackTrace() {
        RuntimeException boom = new RuntimeException("boom");

        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(boom, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getError()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");

        // The regression this closes: handleUnexpected used to swallow `e` with no logging at
        // all, making every unanticipated production failure invisible server-side.
        assertThat(logAppender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy().getClassName()).isEqualTo(RuntimeException.class.getName());
        });
    }

    @Test
    void errorResponse_carriesCorrelationIdFromMdc() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "trace-xyz");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(new IllegalArgumentException("bad"), request);

        assertThat(response.getBody().getCorrelationId()).isEqualTo("trace-xyz");
    }

    @SuppressWarnings("unused")
    private void dummy(String otp) {
    }
}
