package com.locnguyen.ecommerce.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Validates Vietnamese mobile phone numbers.
 *
 * <p>Accepted formats:
 * <ul>
 *   <li>{@code 0[3|5|7|8|9][0-9]{8}} — 10-digit local format</li>
 *   <li>{@code +84[3|5|7|8|9][0-9]{8}} — international format</li>
 * </ul>
 *
 * <p>Null and blank values pass validation (null-safe).
 */
public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {

    /**
     * Vietnamese mobile numbers (Viettel, Vinaphone, Mobifone, Gmobile, Reddi):
     * - 03x, 05x, 07x, 08x, 09x series
     */
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^(0|\\+84)[3-9][0-9]{8}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // null-safe: use @NotBlank for required enforcement
        }
        return PHONE_PATTERN.matcher(value.trim()).matches();
    }
}
