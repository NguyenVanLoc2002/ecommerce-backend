package com.locnguyen.ecommerce.domains.promotion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Schema(description = "Request to create a voucher. Leave 'code' blank to auto-generate one.")
public class CreateVoucherRequest {

    @Size(max = 100)
    @Schema(description = "Leave blank to auto-generate a unique code")
    private String code;

    @NotNull
    private Long promotionId;

    @Min(1)
    @Schema(description = "Total redemption limit. Null means unlimited.")
    private Integer usageLimit;

    @Min(1)
    @Schema(description = "Per-customer redemption limit. Null means unlimited.")
    private Integer usageLimitPerUser;

    @NotNull
    private LocalDateTime startDate;

    @NotNull
    private LocalDateTime endDate;
}
