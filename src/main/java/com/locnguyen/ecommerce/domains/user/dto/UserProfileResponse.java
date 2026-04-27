package com.locnguyen.ecommerce.domains.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.customer.enums.Gender;
import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Combined user profile response — merges User (auth) and Customer (profile) data.
 * Returned by GET/PATCH /api/v1/me.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "User profile — combines auth identity and customer profile")
public class UserProfileResponse {

    @Schema(description = "User ID")
    private final Long id;

    @Schema(description = "Email (login identifier)")
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

    @Schema(description = "Customer ID")
    private final Long customerId;

    @Schema(description = "Gender", example = "MALE")
    private final Gender gender;

    @Schema(description = "Date of birth", example = "2000-01-15")
    private final LocalDate birthDate;

    @Schema(description = "Avatar URL")
    private final String avatarUrl;

    @Schema(description = "Loyalty points balance", example = "0")
    private final Integer loyaltyPoints;

    @Schema(description = "Account creation timestamp")
    private final LocalDateTime createdAt;
}
