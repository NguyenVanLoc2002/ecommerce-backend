package com.locnguyen.ecommerce.domains.admin.dto;

import com.locnguyen.ecommerce.common.validation.PhoneNumber;
import com.locnguyen.ecommerce.domains.customer.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * Admin update for a customer's profile. All fields are optional —
 * only provided fields are applied (partial update).
 *
 * <p>Updates split across the linked entities:
 * <ul>
 *   <li>{@code firstName}, {@code lastName}, {@code phoneNumber} — applied to {@code User}</li>
 *   <li>{@code gender}, {@code birthDate}, {@code avatarUrl} — applied to {@code Customer}</li>
 * </ul>
 *
 * <p>Email, password, status and roles are intentionally not updatable here:
 * status uses the dedicated PATCH /status endpoint and roles cannot be touched
 * through customer APIs (use admin/users for system roles).
 */
@Data
@Schema(description = "Admin update customer profile — only provided fields are updated")
public class UpdateCustomerRequest {

    @Size(max = 100, message = "First name must not exceed 100 characters")
    @Schema(example = "Nguyen")
    private String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    @Schema(example = "Van Loc")
    private String lastName;

    @PhoneNumber
    @Schema(example = "0912345678")
    private String phoneNumber;

    @Schema(description = "Customer gender", example = "MALE")
    private Gender gender;

    @Schema(description = "Date of birth", example = "2000-01-15")
    private LocalDate birthDate;

    @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
    @Schema(description = "Avatar URL", example = "https://cdn.example.com/avatars/abc.jpg")
    private String avatarUrl;
}
