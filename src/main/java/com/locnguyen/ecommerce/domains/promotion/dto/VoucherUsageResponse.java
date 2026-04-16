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
@Schema(description = "Record of a single voucher redemption")
public class VoucherUsageResponse {

    private final Long id;
    private final Long voucherId;
    private final String voucherCode;
    private final Long customerId;
    private final Long orderId;
    private final BigDecimal discountAmount;
    private final LocalDateTime usedAt;
}
