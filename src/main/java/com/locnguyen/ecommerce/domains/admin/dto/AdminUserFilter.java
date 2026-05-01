package com.locnguyen.ecommerce.domains.admin.dto;

import com.locnguyen.ecommerce.domains.user.enums.RoleName;
import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Admin user list filter parameters")
public class AdminUserFilter {

    @Schema(description = "Free-text keyword matched against email, firstName, lastName, phoneNumber",
            example = "loc")
    private String keyword;

    @Schema(description = "Partial, case-insensitive match on email", example = "@example.com")
    private String email;

    @Schema(description = "Partial match on phone number", example = "0912")
    private String phoneNumber;

    @Schema(description = "Filter by account status", example = "ACTIVE")
    private UserStatus status;

    @Schema(description = "Filter by assigned role", example = "ADMIN")
    private RoleName role;

    @Schema(description = "Soft-delete filter: false=active only, true=deleted only", example = "false")
    private Boolean isDeleted;

    @Schema(description = "Include both active and deleted rows", example = "false")
    private Boolean includeDeleted;
}
