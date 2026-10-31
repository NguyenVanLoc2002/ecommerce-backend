package com.locnguyen.ecommerce.domains.promotion.service;

import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.promotion.dto.AddRuleRequest;
import com.locnguyen.ecommerce.domains.promotion.dto.CreatePromotionRequest;
import com.locnguyen.ecommerce.domains.promotion.dto.PromotionFilter;
import com.locnguyen.ecommerce.domains.promotion.dto.PromotionResponse;
import com.locnguyen.ecommerce.domains.promotion.dto.UpdatePromotionRequest;
import com.locnguyen.ecommerce.domains.promotion.entity.Promotion;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PromotionService {

    PromotionResponse createPromotion(CreatePromotionRequest request);

    PromotionResponse updatePromotion(UUID promotionId, UpdatePromotionRequest request);

    void deletePromotion(UUID promotionId);

    PromotionResponse getById(UUID promotionId);

    PagedResponse<PromotionResponse> getPromotions(PromotionFilter filter, Pageable pageable);

    PromotionResponse addRule(UUID promotionId, AddRuleRequest request);

    PromotionResponse removeRule(UUID promotionId, UUID ruleId);

    Promotion findByIdOrThrow(UUID promotionId);

    void incrementUsageCount(UUID promotionId);

    boolean tryIncrementUsageCount(UUID promotionId);

    void decrementUsageCount(UUID promotionId);
}
