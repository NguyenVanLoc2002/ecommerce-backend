package com.locnguyen.ecommerce.domains.promotion.dto;

import com.locnguyen.ecommerce.domains.promotion.enums.DiscountType;
import com.locnguyen.ecommerce.domains.promotion.enums.PromotionScope;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Schema(description = "Request to create a new promotion")
public class CreatePromotionRequest {

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 2000)
    private String description;

    @NotNull
    private DiscountType discountType;

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal discountValue;

    /** Required when discountType is PERCENTAGE to cap the maximum deduction. */
    @DecimalMin("0.01")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal maxDiscountAmount;

    @DecimalMin("0")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal minimumOrderAmount;

    @NotNull
    private PromotionScope scope;

    @NotNull
    private LocalDateTime startDate;

    @NotNull
    private LocalDateTime endDate;

    @Min(1)
    private Integer usageLimit;
}
