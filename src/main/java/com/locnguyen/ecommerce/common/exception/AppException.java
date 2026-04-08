package com.locnguyen.ecommerce.common.exception;

import lombok.Getter;

/**
 * Application-level business exception.
 * Mapped to a structured error response by {@link GlobalExceptionHandler}.
 *
 * <p>Usage:
 * <pre>
 *   throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
 *   throw new AppException(ErrorCode.INVENTORY_NOT_ENOUGH, "Only 3 units available");
 * </pre>
 */
@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AppException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
    }
}
