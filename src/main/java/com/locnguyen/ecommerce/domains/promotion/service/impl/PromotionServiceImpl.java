package com.locnguyen.ecommerce.domains.promotion.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.promotion.dto.AddRuleRequest;
import com.locnguyen.ecommerce.domains.promotion.dto.CreatePromotionRequest;
import com.locnguyen.ecommerce.domains.promotion.dto.PromotionFilter;
import com.locnguyen.ecommerce.domains.promotion.dto.PromotionResponse;
import com.locnguyen.ecommerce.domains.promotion.dto.UpdatePromotionRequest;
import com.locnguyen.ecommerce.domains.promotion.entity.Promotion;
import com.locnguyen.ecommerce.domains.promotion.entity.PromotionRule;
import com.locnguyen.ecommerce.domains.promotion.mapper.PromotionMapper;
import com.locnguyen.ecommerce.domains.promotion.repository.PromotionRepository;
import com.locnguyen.ecommerce.domains.promotion.service.PromotionService;
import com.locnguyen.ecommerce.domains.promotion.specification.PromotionSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionMapper promotionMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public PromotionResponse createPromotion(CreatePromotionRequest request) {
        validateDateRange(request.getStartDate(), request.getEndDate());

        Promotion promotion = new Promotion();
        promotion.setName(request.getName().trim());
        promotion.setDescription(trimToNull(request.getDescription()));
        promotion.setDiscountType(request.getDiscountType());
        promotion.setDiscountValue(request.getDiscountValue());
        promotion.setMaxDiscountAmount(request.getMaxDiscountAmount());
        promotion.setMinimumOrderAmount(request.getMinimumOrderAmount() != null
                ? request.getMinimumOrderAmount()
                : promotion.getMinimumOrderAmount());
        promotion.setScope(request.getScope());
        promotion.setStartDate(request.getStartDate());
        promotion.setEndDate(request.getEndDate());
        promotion.setUsageLimit(request.getUsageLimit());
        promotion.setActive(true);

        promotion = promotionRepository.save(promotion);
        log.info("Promotion created: id={} name={}", promotion.getId(), promotion.getName());
        auditLogService.log(AuditAction.PROMOTION_CREATED, "PROMOTION",
                String.valueOf(promotion.getId()), "name=" + promotion.getName());
        return promotionMapper.toResponse(promotion);
    }

    @Override
    @Transactional
    public PromotionResponse updatePromotion(UUID promotionId, UpdatePromotionRequest request) {
        Promotion promotion = findByIdOrThrow(promotionId);

        if (request.getName() != null) {
            promotion.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            promotion.setDescription(trimToNull(request.getDescription()));
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

        validateDateRange(promotion.getStartDate(), promotion.getEndDate());

        promotion = promotionRepository.save(promotion);
        log.info("Promotion updated: id={}", promotionId);
        auditLogService.log(AuditAction.PROMOTION_UPDATED, "PROMOTION", String.valueOf(promotionId));
        return promotionMapper.toResponse(promotion);
    }

    @Override
    @Transactional
    public void deletePromotion(UUID promotionId) {
        Promotion promotion = findByIdOrThrow(promotionId);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();
        promotion.softDelete(actor);
        promotionRepository.save(promotion);
        log.info("Promotion deleted: id={} by={}", promotionId, actor);
        auditLogService.log(AuditAction.PROMOTION_DELETED, "PROMOTION", String.valueOf(promotionId));
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionResponse getById(UUID promotionId) {
        return promotionMapper.toResponse(findByIdOrThrow(promotionId));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PromotionResponse> getPromotions(PromotionFilter filter, Pageable pageable) {
        Page<Promotion> page = promotionRepository.findAll(
                PromotionSpecification.withFilter(filter), pageable);
        return PagedResponse.of(page.map(promotionMapper::toListItemResponse));
    }

    @Override
    @Transactional
    public PromotionResponse addRule(UUID promotionId, AddRuleRequest request) {
        Promotion promotion = findByIdOrThrow(promotionId);

        PromotionRule rule = new PromotionRule();
        rule.setPromotion(promotion);
        rule.setRuleType(request.getRuleType());
        rule.setRuleValue(request.getRuleValue().trim());
        rule.setDescription(trimToNull(request.getDescription()));
        promotion.getRules().add(rule);

        promotion = promotionRepository.save(promotion);
        log.info("Promotion rule added: promotionId={} ruleType={}", promotionId, rule.getRuleType());
        return promotionMapper.toResponse(promotion);
    }

    @Override
    @Transactional
    public PromotionResponse removeRule(UUID promotionId, UUID ruleId) {
        Promotion promotion = findByIdOrThrow(promotionId);

        boolean removed = promotion.getRules().removeIf(rule -> ruleId.equals(rule.getId()));
        if (!removed) {
            throw new AppException(ErrorCode.PROMOTION_RULE_NOT_FOUND);
        }

        promotion = promotionRepository.save(promotion);
        log.info("Promotion rule removed: promotionId={} ruleId={}", promotionId, ruleId);
        return promotionMapper.toResponse(promotion);
    }

    @Override
    @Transactional(readOnly = true)
    public Promotion findByIdOrThrow(UUID promotionId) {
        return promotionRepository.findByIdAndDeletedFalse(promotionId)
                .orElseThrow(() -> new AppException(ErrorCode.PROMOTION_NOT_FOUND));
    }

    @Override
    @Transactional
    public void incrementUsageCount(UUID promotionId) {
        findByIdOrThrow(promotionId);
        promotionRepository.incrementUsageCount(promotionId);
    }

    @Override
    @Transactional
    public boolean tryIncrementUsageCount(UUID promotionId) {
        findByIdOrThrow(promotionId);
        return promotionRepository.incrementUsageCountIfAvailable(promotionId) == 1;
    }

    @Override
    @Transactional
    public void decrementUsageCount(UUID promotionId) {
        findByIdOrThrow(promotionId);
        promotionRepository.decrementUsageCount(promotionId);
    }

    private void validateDateRange(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "End date must be after start date");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
