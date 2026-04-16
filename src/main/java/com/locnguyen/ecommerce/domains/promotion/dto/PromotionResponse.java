package com.locnguyen.ecommerce.domains.promotion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Promotion response")
public class PromotionResponse {

    private final Long id;
    private final String name;
    private final String description;
    private final String discountType;
    private final BigDecimal discountValue;
    private final BigDecimal maxDiscountAmount;
    private final BigDecimal minimumOrderAmount;
    private final String scope;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final boolean active;
    private final Integer usageLimit;
    private final int usageCount;
    private final List<PromotionRuleResponse> rules;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
