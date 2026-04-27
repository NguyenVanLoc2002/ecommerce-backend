package com.locnguyen.ecommerce.domains.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Authenticated user profile returned on register/login")
public class UserResponse {

    @Schema(description = "User ID")
    private final Long id;

    @Schema(description = "User email (used as login)")
    private final String email;

    @Schema(description = "First name")
    private final String firstName;

    @Schema(description = "Last name")
    private final String lastName;

    @Schema(description = "Phone number")
    private final String phoneNumber;

    @Schema(description = "Account status", example = "ACTIVE")
    private final UserStatus status;

    @Schema(description = "Assigned roles", example = "[\"CUSTOMER\"]")
    private final Set<String> roles;

    @Schema(description = "Account creation timestamp")
    private final LocalDateTime createdAt;
}
