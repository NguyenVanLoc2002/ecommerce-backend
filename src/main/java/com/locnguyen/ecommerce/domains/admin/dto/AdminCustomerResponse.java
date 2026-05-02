package com.locnguyen.ecommerce.domains.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.customer.enums.Gender;
import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Admin view of a customer joined with the linked user account")
public class AdminCustomerResponse {

    @Schema(description = "Customer ID")
    private final UUID id;

    @Schema(description = "Linked user ID (authentication identity)")
    private final UUID userId;

    @Schema(description = "Login email")
    private final String email;

    @Schema(description = "First name (from user)")
    private final String firstName;

    @Schema(description = "Last name (from user)")
    private final String lastName;

    @Schema(description = "Phone number (from user)")
    private final String phoneNumber;

    @Schema(description = "Linked user account status", example = "ACTIVE")
    private final UserStatus status;

    @Schema(description = "Customer gender", example = "MALE")
    private final Gender gender;

    @Schema(description = "Date of birth")
    private final LocalDate birthDate;

    @Schema(description = "Avatar URL")
    private final String avatarUrl;

    @Schema(description = "Loyalty points")
    private final Integer loyaltyPoints;

    @Schema(description = "Customer profile creation timestamp")
    private final LocalDateTime createdAt;

    @Schema(description = "Customer profile last-update timestamp")
    private final LocalDateTime updatedAt;
}
