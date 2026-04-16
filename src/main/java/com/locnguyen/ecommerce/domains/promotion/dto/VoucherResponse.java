package com.locnguyen.ecommerce.domains.promotion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Voucher response")
public class VoucherResponse {

    private final Long id;
    private final String code;
    private final Long promotionId;
    private final String promotionName;
    private final String discountType;
    private final BigDecimal discountValue;
    private final BigDecimal maxDiscountAmount;
    private final BigDecimal minimumOrderAmount;
    private final Integer usageLimit;
    private final int usageCount;
    private final Integer usageLimitPerUser;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final boolean active;
    private final LocalDateTime createdAt;
}
