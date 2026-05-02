package com.locnguyen.ecommerce.domains.admin.dto;

import com.locnguyen.ecommerce.common.validation.PhoneNumber;
import com.locnguyen.ecommerce.domains.user.enums.RoleName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.Set;

@Data
@Schema(description = "Admin request to create a system user with explicit role assignment")
public class CreateUserRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    @Schema(example = "staff@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, and one digit")
    @Schema(example = "AdminPass123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    @Schema(example = "Nguyen", requiredMode = Schema.RequiredMode.REQUIRED)
    private String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    @Schema(example = "Van Loc")
    private String lastName;

    @PhoneNumber
    @Schema(example = "0912345678")
    private String phoneNumber;

    @NotEmpty(message = "At least one role is required")
    @Schema(description = "System roles to assign. Only STAFF, ADMIN, and SUPER_ADMIN are accepted; " +
            "CUSTOMER is rejected (use the public /auth/register API to create customer accounts).",
            example = "[\"STAFF\"]", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"STAFF", "ADMIN", "SUPER_ADMIN"})
    private Set<RoleName> roles;
}
