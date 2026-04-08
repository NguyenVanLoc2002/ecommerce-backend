package com.locnguyen.ecommerce.common.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents a single field validation error in an error response.
 */
@Getter
@AllArgsConstructor
public class FieldError {

    private final String field;
    private final String message;
}
