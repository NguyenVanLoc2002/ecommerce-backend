package com.locnguyen.ecommerce.domains.promotion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Result of voucher validation — includes the discount preview")
public class ValidateVoucherResponse {

    private final String voucherCode;
    private final String promotionName;
    private final String discountType;
    private final BigDecimal discountValue;

    /** Calculated monetary discount for the given order amount. */
    private final BigDecimal discountAmount;

    /** Original order amount before discount. */
    private final BigDecimal orderAmount;

    /** Final order amount after discount (orderAmount − discountAmount, minimum 0). */
    private final BigDecimal finalAmount;
}
