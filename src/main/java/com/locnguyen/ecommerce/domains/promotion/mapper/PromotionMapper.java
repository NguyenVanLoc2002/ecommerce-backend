package com.locnguyen.ecommerce.domains.promotion.mapper;

import com.locnguyen.ecommerce.domains.promotion.dto.PromotionResponse;
import com.locnguyen.ecommerce.domains.promotion.dto.PromotionRuleResponse;
import com.locnguyen.ecommerce.domains.promotion.entity.Promotion;
import com.locnguyen.ecommerce.domains.promotion.entity.PromotionRule;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PromotionMapper {

    /**
     * Full response including rules list.
     * Use for single-item GET endpoints.
     */
    default PromotionResponse toResponse(Promotion promotion) {
        if (promotion == null) return null;

        return PromotionResponse.builder()
                .id(promotion.getId())
                .name(promotion.getName())
                .description(promotion.getDescription())
                .discountType(promotion.getDiscountType().name())
                .discountValue(promotion.getDiscountValue())
                .maxDiscountAmount(promotion.getMaxDiscountAmount())
                .minimumOrderAmount(promotion.getMinimumOrderAmount())
                .scope(promotion.getScope().name())
                .startDate(promotion.getStartDate())
                .endDate(promotion.getEndDate())
                .active(promotion.isActive())
                .usageLimit(promotion.getUsageLimit())
                .usageCount(promotion.getUsageCount())
                .rules(toRuleResponses(promotion.getRules()))
                .createdAt(promotion.getCreatedAt())
                .updatedAt(promotion.getUpdatedAt())
                .build();
    }

    /**
     * Lightweight response without rules.
     * Use for paginated list views.
     */
    default PromotionResponse toListItemResponse(Promotion promotion) {
        if (promotion == null) return null;

        return PromotionResponse.builder()
                .id(promotion.getId())
                .name(promotion.getName())
                .discountType(promotion.getDiscountType().name())
                .discountValue(promotion.getDiscountValue())
                .maxDiscountAmount(promotion.getMaxDiscountAmount())
                .minimumOrderAmount(promotion.getMinimumOrderAmount())
                .scope(promotion.getScope().name())
                .startDate(promotion.getStartDate())
                .endDate(promotion.getEndDate())
                .active(promotion.isActive())
                .usageLimit(promotion.getUsageLimit())
                .usageCount(promotion.getUsageCount())
                .createdAt(promotion.getCreatedAt())
                .build();
    }

    default PromotionRuleResponse toRuleResponse(PromotionRule rule) {
        if (rule == null) return null;

        return PromotionRuleResponse.builder()
                .id(rule.getId())
                .ruleType(rule.getRuleType().name())
                .ruleValue(rule.getRuleValue())
                .description(rule.getDescription())
                .build();
    }

    List<PromotionRuleResponse> toRuleResponses(List<PromotionRule> rules);
}
