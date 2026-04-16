package com.locnguyen.ecommerce.domains.promotion.dto;

import com.locnguyen.ecommerce.domains.promotion.enums.RuleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to add a rule to a promotion")
public class AddRuleRequest {

    @NotNull
    private RuleType ruleType;

    @NotBlank
    @Size(max = 500)
    @Schema(description = "Value depends on ruleType: decimal for MIN_ORDER_AMOUNT, " +
            "comma-separated IDs for SPECIFIC_PRODUCTS/CATEGORIES/BRANDS, 'true' for FIRST_ORDER")
    private String ruleValue;

    @Size(max = 255)
    private String description;
}
