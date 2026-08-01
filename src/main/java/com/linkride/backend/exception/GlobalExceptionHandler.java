package com.linkride.backend.exception;

import com.linkride.backend.dto.ErrorResponse;
import com.linkride.backend.filter.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Central mapping from thrown exceptions to the {@link ErrorResponse} envelope (Phase 5 §1 —
 * see backend/docs/phase-5-platform-hardening.md). Service methods throw
 * {@link IllegalArgumentException}, {@link ResponseStatusException}, or a framework exception
 * and let this advice translate it into the right HTTP response, instead of duplicating
 * try/catch blocks per controller.
 *
 * <p>{@link ResourceNotFoundException} and {@link BusinessRuleViolationException} exist so
 * services that need more than one distinct {@code code} at the same HTTP status (e.g.
 * {@code USER_NOT_FOUND} vs. {@code FAVORITE_NOT_FOUND}, both 404) can still throw through this
 * advice instead of building {@link ErrorResponse} locally — the code travels with the
 * exception rather than being inferred from its Java type.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage(), request, null);
    }

    /** See {@link ResourceNotFoundException} — the exception carries its own machine-readable code. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, e.getCode(), e.getMessage(), request, null);
    }

    /** See {@link BusinessRuleViolationException} — the exception carries its own machine-readable code. */
    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRuleViolation(BusinessRuleViolationException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, e.getCode(), e.getMessage(), request, null);
    }

    /**
     * {@code @Valid @RequestBody} failures (Phase 4.7) — now reports every invalid field via
     * {@code details}, not just the first one.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<ErrorResponse.FieldViolation> details = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        String message = firstViolationMessage(details, "Validation failed");
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request, details);
    }

    /** {@code @Validated} path-variable / request-param constraint failures. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        List<ErrorResponse.FieldViolation> details = e.getConstraintViolations().stream()
                .map(v -> new ErrorResponse.FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        String message = firstViolationMessage(details, "Validation failed");
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request, details);
    }

    /** Missing or unparseable {@code @RequestBody} (malformed JSON, wrong type, empty body). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(HttpMessageNotReadableException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST_BODY",
                "Request body is missing or malformed", request, null);
    }

    /** A path-variable or request-param couldn't be converted to its declared type. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Parameter '" + e.getName() + "' has an invalid value", request, null);
    }

    /**
     * A required {@code @RequestParam} (e.g. {@code lat}/{@code lng} on {@code GET /api/v1/home})
     * was omitted entirely. Thrown by Spring's own argument resolution before the controller
     * method runs, and previously escaped this advice, falling through to Spring Boot's default
     * error body instead of the {@link ErrorResponse} envelope.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Required parameter '" + e.getParameterName() + "' is missing", request, null);
    }

    /** Requires {@code spring.mvc.throw-exception-if-no-handler-found=true} (set in application.properties). */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFound(NoHandlerFoundException e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND",
                "No endpoint matches " + e.getHttpMethod() + " " + e.getRequestURL(), request, null);
    }

    /**
     * Thrown by application code (e.g. a resource-ownership check) that propagates up through a
     * normal controller call. Spring Security's own filter-chain authorization denials bypass
     * the dispatcher entirely and are handled separately by
     * {@link com.linkride.backend.config.RestAccessDeniedHandler}, on the same envelope.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "You do not have permission to perform this action", request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required", request, null);
    }

    /** Unique-constraint / FK violations that slip through service-layer checks (e.g. a race). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e, HttpServletRequest request) {
        log.warn("Data integrity violation on [{}]: {}", request.getRequestURI(), e.getMessage());
        return build(HttpStatus.CONFLICT, "DATA_CONFLICT", "The request conflicts with existing data", request, null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException e, HttpServletRequest request) {
        return build(e.getStatusCode(), e.getStatusCode().toString(), e.getReason(), request, null);
    }

    /**
     * Catch-all for anything not explicitly mapped above. Previously returned a safe response
     * without logging the exception at all -- every unanticipated production failure was
     * invisible server-side. This is the single most urgent fix in Phase 5 (see the doc's Final
     * Review): log the full stack trace, keyed by correlation ID, before returning the safe
     * envelope.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception on [{}]: {}",
                request != null ? request.getRequestURI() : "unknown", e.getMessage(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request, null);
    }

    private String firstViolationMessage(List<ErrorResponse.FieldViolation> details, String fallback) {
        return details.isEmpty() ? fallback : details.get(0).getField() + ": " + details.get(0).getMessage();
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatusCode status,
            String code,
            String message,
            HttpServletRequest request,
            List<ErrorResponse.FieldViolation> details) {

        ErrorResponse body = ErrorResponse.builder()
                .error(code)
                .code(code)
                .message(message)
                .path(request != null ? request.getRequestURI() : null)
                .timestamp(OffsetDateTime.now())
                .correlationId(MDC.get(CorrelationIdFilter.MDC_KEY))
                .details(details)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
