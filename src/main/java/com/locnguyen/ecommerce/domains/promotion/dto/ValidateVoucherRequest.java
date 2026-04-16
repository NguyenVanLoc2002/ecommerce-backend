package com.locnguyen.ecommerce.domains.promotion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Schema(description = "Request to validate a voucher and preview the discount")
public class ValidateVoucherRequest {

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 16, fraction = 2)
    @Schema(description = "Order subtotal before discount")
    private BigDecimal orderAmount;

    @Schema(description = "Product IDs in the order (required for PRODUCT-scope promotions)")
    private List<Long> productIds;

    @Schema(description = "Category IDs of products in the order (required for CATEGORY-scope)")
    private List<Long> categoryIds;

    @Schema(description = "Brand IDs of products in the order (required for BRAND-scope)")
    private List<Long> brandIds;
}
