package com.locnguyen.ecommerce.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates Vietnamese phone number format.
 *
 * <p>Accepted formats:
 * <ul>
 *   <li>{@code 0912345678}  — 10 digits starting with 0</li>
 *   <li>{@code +84912345678} — international format</li>
 * </ul>
 *
 * <p>Null/blank values pass validation — combine with {@code @NotBlank} if the field is required.
 *
 * <p>Usage:
 * <pre>
 *   {@literal @}NotBlank
 *   {@literal @}PhoneNumber
 *   private String phoneNumber;
 * </pre>
 */
@Documented
@Constraint(validatedBy = PhoneNumberValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface PhoneNumber {

    String message() default "Invalid phone number. Expected format: 0xxxxxxxxx or +84xxxxxxxxx";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
