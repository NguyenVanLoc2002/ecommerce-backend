package com.locnguyen.ecommerce.domains.promotion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Schema(description = "Request to update an existing promotion. Null fields are ignored.")
public class UpdatePromotionRequest {

    @Size(max = 200)
    private String name;

    @Size(max = 2000)
    private String description;

    @DecimalMin("0.01")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal discountValue;

    @DecimalMin("0.01")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal maxDiscountAmount;

    @DecimalMin("0")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal minimumOrderAmount;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private Boolean active;

    @Min(1)
    private Integer usageLimit;
}
