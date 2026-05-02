package com.locnguyen.ecommerce.domains.admin.dto;

import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Admin update customer account status — propagates to the linked User")
public class UpdateCustomerStatusRequest {

    @NotNull(message = "Status is required")
    @Schema(description = "New status for the linked user account",
            example = "LOCKED", requiredMode = Schema.RequiredMode.REQUIRED)
    private UserStatus status;
}
