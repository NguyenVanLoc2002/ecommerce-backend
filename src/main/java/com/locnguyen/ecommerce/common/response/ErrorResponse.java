package com.locnguyen.ecommerce.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response for all API error cases.
 *
 * <pre>
 * {
 *   "success": false,
 *   "code": "VALIDATION_ERROR",
 *   "message": "Validation failed",
 *   "errors": [{ "field": "email", "message": "must not be blank" }],
 *   "timestamp": "2026-04-08T10:00:00Z",
 *   "path": "/api/v1/auth/register"
 * }
 * </pre>
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final boolean success;
    private final String code;
    private final String message;
    private final List<FieldError> errors;
    private final String timestamp;
    private final String path;

    // ─── Factory methods ────────────────────────────────────────────────────

    /**
     * Build from an {@link ErrorCode} using its default message.
     */
    public static ErrorResponse of(ErrorCode errorCode, String path) {
        return ErrorResponse.builder()
                .success(false)
                .code(errorCode.getCode())
                .message(errorCode.getDefaultMessage())
                .timestamp(Instant.now().toString())
                .path(path)
                .build();
    }

    /**
     * Build from an {@link ErrorCode} with a custom message override.
     */
    public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
        return ErrorResponse.builder()
                .success(false)
                .code(errorCode.getCode())
                .message(message)
                .timestamp(Instant.now().toString())
                .path(path)
                .build();
    }

    /**
     * Build with explicit code string (for cases without a matching {@link ErrorCode}).
     */
    public static ErrorResponse of(String code, String message, String path) {
        return ErrorResponse.builder()
                .success(false)
                .code(code)
                .message(message)
                .timestamp(Instant.now().toString())
                .path(path)
                .build();
    }

    /**
     * Build a validation error response with field-level error details.
     */
    public static ErrorResponse validation(String code, String message,
                                           List<FieldError> errors, String path) {
        return ErrorResponse.builder()
                .success(false)
                .code(code)
                .message(message)
                .errors(errors)
                .timestamp(Instant.now().toString())
                .path(path)
                .build();
    }
}
