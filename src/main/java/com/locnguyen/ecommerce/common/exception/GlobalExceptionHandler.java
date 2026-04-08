package com.locnguyen.ecommerce.common.exception;

import com.locnguyen.ecommerce.common.response.ErrorResponse;
import com.locnguyen.ecommerce.common.response.FieldError;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Central exception handler for all controllers.
 *
 * <p>Handler priority (most specific → least specific):
 * <ol>
 *   <li>{@link AppException} — business rule violations</li>
 *   <li>Validation exceptions — DTO and path/query params</li>
 *   <li>HTTP protocol errors — wrong method, media type, missing param</li>
 *   <li>Security — authentication and authorization</li>
 *   <li>Infrastructure — DB constraint violations</li>
 *   <li>{@link Exception} — catch-all fallback</li>
 * </ol>
 *
 * <p><strong>Security note:</strong> DB error messages, stack traces, and
 * internal paths are never exposed in responses. All internal details go to
 * server-side logs only.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ════════════════════════════════════════════════════════════════════════
    // Business exceptions
    // ════════════════════════════════════════════════════════════════════════

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(
            AppException ex, HttpServletRequest request) {

        log.warn("AppException [{}]: {} — path={}",
                ex.getErrorCode().getCode(), ex.getMessage(), request.getRequestURI());

        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), request.getRequestURI()));
    }

    // ════════════════════════════════════════════════════════════════════════
    // Validation exceptions
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Handles @Valid on request body DTOs.
     * Returns field-level error list.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .collect(Collectors.toList());

        // Also capture object-level errors (cross-field constraints)
        ex.getBindingResult().getGlobalErrors().forEach(ge ->
                fieldErrors.add(new FieldError(ge.getObjectName(), ge.getDefaultMessage())));

        log.debug("Validation failed for {}: {} error(s) — path={}",
                ex.getObjectName(), fieldErrors.size(), request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.validation(
                        ErrorCode.VALIDATION_ERROR.getCode(),
                        "Validation failed",
                        fieldErrors,
                        request.getRequestURI()
                ));
    }

    /**
     * Handles @Validated on path/query param methods.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(cv -> {
                    String propertyPath = cv.getPropertyPath().toString();
                    // Strip method prefix: "methodName.fieldName" → "fieldName"
                    String field = propertyPath.contains(".")
                            ? propertyPath.substring(propertyPath.lastIndexOf('.') + 1)
                            : propertyPath;
                    return new FieldError(field, cv.getMessage());
                })
                .collect(Collectors.toList());

        log.debug("Constraint violation: {} error(s) — path={}",
                fieldErrors.size(), request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.validation(
                        ErrorCode.VALIDATION_ERROR.getCode(),
                        "Validation failed",
                        fieldErrors,
                        request.getRequestURI()
                ));
    }

    // ════════════════════════════════════════════════════════════════════════
    // HTTP protocol exceptions
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 400 — Malformed or unreadable request body.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.warn("Malformed request body at path={}", request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        ErrorCode.BAD_REQUEST.getCode(),
                        "Malformed or unreadable request body",
                        request.getRequestURI()
                ));
    }

    /**
     * 400 — Missing required query or form parameter.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        ErrorCode.BAD_REQUEST.getCode(),
                        "Required parameter '" + ex.getParameterName() + "' is missing",
                        request.getRequestURI()
                ));
    }

    /**
     * 400 — Path or query param has incompatible type (e.g., string for Long id).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        ErrorCode.BAD_REQUEST.getCode(),
                        "Invalid value for parameter '" + ex.getName() + "'",
                        request.getRequestURI()
                ));
    }

    /**
     * 404 — No handler found for the requested URL.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        ErrorCode.NOT_FOUND.getCode(),
                        "The requested endpoint was not found",
                        request.getRequestURI()
                ));
    }

    /**
     * 404 — JPA EntityNotFoundException (e.g., getById proxy access on deleted entity).
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(
            EntityNotFoundException ex, HttpServletRequest request) {

        log.warn("EntityNotFoundException at path={}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        ErrorCode.NOT_FOUND.getCode(),
                        "Resource not found",
                        request.getRequestURI()
                ));
    }

    /**
     * 405 — HTTP method not supported (e.g., POST on a GET-only endpoint).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        log.warn("Method not allowed: {} {} — allowed={}",
                ex.getMethod(), request.getRequestURI(), ex.getSupportedHttpMethods());

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse.of(
                        "METHOD_NOT_ALLOWED",
                        "HTTP method '" + ex.getMethod() + "' is not supported for this endpoint",
                        request.getRequestURI()
                ));
    }

    /**
     * 415 — Unsupported media type (e.g., XML body when JSON expected).
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponse.of(
                        "UNSUPPORTED_MEDIA_TYPE",
                        "Content type '" + ex.getContentType() + "' is not supported. Use 'application/json'",
                        request.getRequestURI()
                ));
    }

    // ════════════════════════════════════════════════════════════════════════
    // Security exceptions
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 403 — Authenticated user lacks the required role/permission.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        log.warn("Access denied: path={}", request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(ErrorCode.FORBIDDEN, request.getRequestURI()));
    }

    /**
     * 401 — Authentication required but not provided or token is invalid.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {

        log.warn("Authentication failure: path={}", request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(ErrorCode.UNAUTHORIZED, request.getRequestURI()));
    }

    // ════════════════════════════════════════════════════════════════════════
    // Infrastructure exceptions
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 409 — Database unique constraint or FK violation.
     *
     * <p>The raw DB error message is intentionally suppressed in the response
     * to avoid leaking internal schema details. The full cause is logged server-side.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        log.warn("Data integrity violation at path={}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        ErrorCode.CONFLICT.getCode(),
                        "A data conflict occurred. The resource may already exist.",
                        request.getRequestURI()
                ));
    }

    // ════════════════════════════════════════════════════════════════════════
    // Catch-all fallback
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 500 — Any unhandled exception. Stack trace is logged but NEVER exposed in response.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest request) {

        log.error("Unhandled exception at path={} method={}: {}",
                request.getRequestURI(), request.getMethod(), ex.getMessage(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, request.getRequestURI()));
    }
}
