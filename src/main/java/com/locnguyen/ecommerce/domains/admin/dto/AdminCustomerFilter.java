package com.locnguyen.ecommerce.domains.admin.dto;

import com.locnguyen.ecommerce.domains.customer.enums.Gender;
import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Admin customer list filter parameters")
public class AdminCustomerFilter {

    @Schema(description = "Free-text keyword matched against email, firstName, lastName, phoneNumber",
            example = "loc")
    private String keyword;

    @Schema(description = "Partial, case-insensitive match on email", example = "@example.com")
    private String email;

    @Schema(description = "Partial match on phone number", example = "0912")
    private String phoneNumber;

    @Schema(description = "Filter by linked user account status", example = "ACTIVE")
    private UserStatus status;

    @Schema(description = "Filter by gender", example = "FEMALE")
    private Gender gender;

    @Schema(description = "Inclusive lower bound on loyalty points", example = "100")
    private Integer minLoyaltyPoints;

    @Schema(description = "Inclusive upper bound on loyalty points", example = "5000")
    private Integer maxLoyaltyPoints;

    @Schema(description = "Inclusive lower bound on customer createdAt date (ISO date)",
            example = "2026-01-01")
    private LocalDate dateFrom;

    @Schema(description = "Inclusive upper bound on customer createdAt date (ISO date)",
            example = "2026-12-31")
    private LocalDate dateTo;

    @Schema(description = "Soft-delete filter: false=active only (default), true=deleted only",
            example = "false")
    private Boolean isDeleted;

    @Schema(description = "Include both active and deleted rows", example = "false")
    private Boolean includeDeleted;
}
