package com.locnguyen.ecommerce.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Standard success response wrapper for all API endpoints.
 *
 * <pre>
 * {
 *   "success": true,
 *   "code": "SUCCESS",
 *   "message": "...",
 *   "data": { ... },
 *   "timestamp": "2026-04-08T10:00:00Z"
 * }
 * </pre>
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;
    private final String timestamp;

    // ─── Factory methods ────────────────────────────────────────────────────

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code("SUCCESS")
                .message("Request processed successfully")
                .data(data)
                .timestamp(Instant.now().toString())
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code("SUCCESS")
                .message(message)
                .data(data)
                .timestamp(Instant.now().toString())
                .build();
    }

    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code("SUCCESS")
                .message("Created successfully")
                .data(data)
                .timestamp(Instant.now().toString())
                .build();
    }

    public static ApiResponse<Void> noContent() {
        return ApiResponse.<Void>builder()
                .success(true)
                .code("SUCCESS")
                .message("Operation completed successfully")
                .data(null)
                .timestamp(Instant.now().toString())
                .build();
    }
}
