package com.locnguyen.ecommerce.domains.admin.dto;

import com.locnguyen.ecommerce.common.validation.PhoneNumber;
import com.locnguyen.ecommerce.domains.user.enums.RoleName;
import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

/**
 * Admin update for a system user. All fields are optional — only provided fields are applied.
 *
 * <p>Password update is intentionally not supported here. Email is also read-only via this
 * endpoint to keep login identifiers stable; create a new account if email must change.
 */
@Data
@Schema(description = "Admin update system user request — only provided fields are updated")
public class UpdateUserRequest {

    @Size(max = 100, message = "First name must not exceed 100 characters")
    @Schema(example = "Nguyen")
    private String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    @Schema(example = "Van Loc")
    private String lastName;

    @PhoneNumber
    @Schema(example = "0912345678")
    private String phoneNumber;

    @Schema(description = "Account status", example = "ACTIVE")
    private UserStatus status;

    @Schema(description = "Replace assigned system roles. Only STAFF, ADMIN, and SUPER_ADMIN are accepted; " +
            "CUSTOMER is rejected. Must be non-empty when provided.",
            example = "[\"STAFF\"]",
            allowableValues = {"STAFF", "ADMIN", "SUPER_ADMIN"})
    private Set<RoleName> roles;
}
