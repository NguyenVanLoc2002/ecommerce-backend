package com.locnguyen.ecommerce.domains.promotion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Promotion rule response")
public class PromotionRuleResponse {

    private final Long id;
    private final String ruleType;
    private final String ruleValue;
    private final String description;
}
