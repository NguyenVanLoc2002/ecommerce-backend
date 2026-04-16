package com.locnguyen.ecommerce.domains.promotion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Schema(description = "Request to update a voucher. Null fields are ignored.")
public class UpdateVoucherRequest {

    @Min(1)
    private Integer usageLimit;

    @Min(1)
    private Integer usageLimitPerUser;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private Boolean active;
}
