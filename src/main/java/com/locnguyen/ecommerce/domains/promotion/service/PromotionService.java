package com.locnguyen.ecommerce.domains.promotion.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.promotion.dto.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.locnguyen.ecommerce.domains.promotion.entity.Promotion;
import com.locnguyen.ecommerce.domains.promotion.entity.PromotionRule;
import com.locnguyen.ecommerce.domains.promotion.mapper.PromotionMapper;
import com.locnguyen.ecommerce.domains.promotion.repository.PromotionRepository;
import com.locnguyen.ecommerce.domains.promotion.repository.PromotionRuleRepository;
import com.locnguyen.ecommerce.domains.promotion.specification.PromotionSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionRuleRepository promotionRuleRepository;
    private final PromotionMapper promotionMapper;

    // ─── Admin CRUD ──────────────────────────────────────────────────────────

    @Transactional
    public PromotionResponse createPromotion(CreatePromotionRequest request) {
        validateDateRange(request.getStartDate(), request.getEndDate());

        Promotion promotion = new Promotion();
        promotion.setName(request.getName().trim());
        promotion.setDescription(request.getDescription());
        promotion.setDiscountType(request.getDiscountType());
        promotion.setDiscountValue(request.getDiscountValue());
        promotion.setMaxDiscountAmount(request.getMaxDiscountAmount());
        promotion.setMinimumOrderAmount(
                request.getMinimumOrderAmount() != null
                        ? request.getMinimumOrderAmount()
                        : BigDecimal.ZERO);
        promotion.setScope(request.getScope());
        promotion.setStartDate(request.getStartDate());
        promotion.setEndDate(request.getEndDate());
        promotion.setUsageLimit(request.getUsageLimit());

        promotion = promotionRepository.save(promotion);
        log.info("Promotion created: id={} name={}", promotion.getId(), promotion.getName());
        return promotionMapper.toResponse(promotion);
    }

    @Transactional
    public PromotionResponse updatePromotion(Long promotionId, UpdatePromotionRequest request) {
        Promotion promotion = findByIdOrThrow(promotionId);

        if (request.getName() != null) {
            promotion.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            promotion.setDescription(request.getDescription());
        }
        if (request.getDiscountValue() != null) {
            promotion.setDiscountValue(request.getDiscountValue());
        }
        if (request.getMaxDiscountAmount() != null) {
            promotion.setMaxDiscountAmount(request.getMaxDiscountAmount());
        }
        if (request.getMinimumOrderAmount() != null) {
            promotion.setMinimumOrderAmount(request.getMinimumOrderAmount());
        }
        if (request.getStartDate() != null) {
            promotion.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            promotion.setEndDate(request.getEndDate());
        }
        if (request.getActive() != null) {
            promotion.setActive(request.getActive());
        }
        if (request.getUsageLimit() != null) {
            promotion.setUsageLimit(request.getUsageLimit());
        }

        if (promotion.getStartDate() != null && promotion.getEndDate() != null) {
            validateDateRange(promotion.getStartDate(), promotion.getEndDate());
        }

        promotion = promotionRepository.save(promotion);
        log.info("Promotion updated: id={}", promotion.getId());
        return promotionMapper.toResponse(promotion);
    }

    @Transactional
    public void deletePromotion(Long promotionId) {
        Promotion promotion = findByIdOrThrow(promotionId);
        promotion.softDelete(SecurityUtils.getCurrentUsernameOrSystem());
        promotionRepository.save(promotion);
        log.info("Promotion soft-deleted: id={}", promotionId);
    }

    @Transactional(readOnly = true)
    public PromotionResponse getById(Long promotionId) {
        return promotionMapper.toResponse(findByIdOrThrow(promotionId));
    }

    @Transactional(readOnly = true)
    public PagedResponse<PromotionResponse> getPromotions(PromotionFilter filter, Pageable pageable) {
        Page<Promotion> page = promotionRepository.findAll(
                PromotionSpecification.withFilter(filter), pageable);
        return PagedResponse.of(page.map(promotionMapper::toListItemResponse));
    }

    // ─── Rule management ─────────────────────────────────────────────────────

    @Transactional
    public PromotionResponse addRule(Long promotionId, AddRuleRequest request) {
        Promotion promotion = findByIdOrThrow(promotionId);

        PromotionRule rule = new PromotionRule();
        rule.setPromotion(promotion);
        rule.setRuleType(request.getRuleType());
        rule.setRuleValue(request.getRuleValue().trim());
        rule.setDescription(request.getDescription());

        promotionRuleRepository.save(rule);
        log.info("Rule added to promotion {}: type={}", promotionId, request.getRuleType());

        // Return fresh entity with updated rules list
        return promotionMapper.toResponse(findByIdOrThrow(promotionId));
    }

    @Transactional
    public PromotionResponse removeRule(Long promotionId, Long ruleId) {
        findByIdOrThrow(promotionId);

        PromotionRule rule = promotionRuleRepository.findById(ruleId)
                .orElseThrow(() -> new AppException(ErrorCode.PROMOTION_RULE_NOT_FOUND));

        if (!rule.getPromotion().getId().equals(promotionId)) {
            throw new AppException(ErrorCode.PROMOTION_RULE_NOT_FOUND);
        }

        promotionRuleRepository.delete(rule);
        log.info("Rule {} removed from promotion {}", ruleId, promotionId);
        return promotionMapper.toResponse(findByIdOrThrow(promotionId));
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    // Intentionally package-private — only VoucherService (same package) and internal methods need this.
    Promotion findByIdOrThrow(Long promotionId) {
        return promotionRepository.findById(promotionId)
                .orElseThrow(() -> new AppException(ErrorCode.PROMOTION_NOT_FOUND));
    }

    /**
     * Atomically increments the promotion usage counter.
     * Called by {@code VoucherService} after a successful voucher redemption.
     */
    @Transactional
    public void incrementUsageCount(Long promotionId) {
        if (!promotionRepository.existsById(promotionId)) {
            throw new AppException(ErrorCode.PROMOTION_NOT_FOUND);
        }
        promotionRepository.incrementUsageCount(promotionId);
    }

    /**
     * Atomically decrements the promotion usage counter on order cancellation.
     * Floored at zero by the repository query.
     */
    @Transactional
    public void decrementUsageCount(Long promotionId) {
        if (!promotionRepository.existsById(promotionId)) {
            throw new AppException(ErrorCode.PROMOTION_NOT_FOUND);
        }
        promotionRepository.decrementUsageCount(promotionId);
    }

    private void validateDateRange(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "End date must be after start date");
        }
    }
}
