package com.locnguyen.ecommerce.domains.promotion.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.common.utils.CodeGenerator;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.promotion.dto.*;
import com.locnguyen.ecommerce.domains.promotion.entity.Promotion;
import com.locnguyen.ecommerce.domains.promotion.entity.PromotionRule;
import com.locnguyen.ecommerce.domains.promotion.entity.Voucher;
import com.locnguyen.ecommerce.domains.promotion.entity.VoucherUsage;
import com.locnguyen.ecommerce.domains.promotion.enums.DiscountType;
import com.locnguyen.ecommerce.domains.promotion.enums.RuleType;
import com.locnguyen.ecommerce.domains.promotion.mapper.VoucherMapper;
import com.locnguyen.ecommerce.domains.promotion.repository.VoucherRepository;
import com.locnguyen.ecommerce.domains.promotion.repository.VoucherUsageRepository;
import com.locnguyen.ecommerce.domains.promotion.specification.VoucherSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import java.util.UUID;
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final VoucherMapper voucherMapper;
    private final PromotionService promotionService;
    private final OrderRepository orderRepository;

    // ─── Admin CRUD ──────────────────────────────────────────────────────────

    @Transactional
    public VoucherResponse createVoucher(CreateVoucherRequest request) {
        Promotion promotion = promotionService.findByIdOrThrow(request.getPromotionId());
        validateDateRange(request.getStartDate(), request.getEndDate());

        String code = resolveCode(request.getCode());

        if (voucherRepository.existsByCodeIgnoreCase(code)) {
            throw new AppException(ErrorCode.VOUCHER_CODE_ALREADY_EXISTS,
                    "Voucher code already exists: " + code);
        }

        Voucher voucher = new Voucher();
        voucher.setCode(code.toUpperCase());
        voucher.setPromotion(promotion);
        voucher.setUsageLimit(request.getUsageLimit());
        voucher.setUsageLimitPerUser(request.getUsageLimitPerUser());
        voucher.setStartDate(request.getStartDate());
        voucher.setEndDate(request.getEndDate());

        voucher = voucherRepository.save(voucher);
        log.info("Voucher created: code={} promotionId={}", voucher.getCode(), promotion.getId());
        return voucherMapper.toResponse(voucher);
    }

    @Transactional
    public VoucherResponse updateVoucher(UUID voucherId, UpdateVoucherRequest request) {
        Voucher voucher = findByIdOrThrow(voucherId);

        if (request.getUsageLimit() != null) {
            voucher.setUsageLimit(request.getUsageLimit());
        }
        if (request.getUsageLimitPerUser() != null) {
            voucher.setUsageLimitPerUser(request.getUsageLimitPerUser());
        }
        if (request.getStartDate() != null) {
            voucher.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            voucher.setEndDate(request.getEndDate());
        }
        if (request.getActive() != null) {
            voucher.setActive(request.getActive());
        }

        if (voucher.getStartDate() != null && voucher.getEndDate() != null) {
            validateDateRange(voucher.getStartDate(), voucher.getEndDate());
        }

        voucher = voucherRepository.save(voucher);
        log.info("Voucher updated: id={}", voucher.getId());
        return voucherMapper.toResponse(voucher);
    }

    @Transactional
    public void deleteVoucher(UUID voucherId) {
        Voucher voucher = findByIdOrThrow(voucherId);
        voucher.softDelete(SecurityUtils.getCurrentUsernameOrSystem());
        voucherRepository.save(voucher);
        log.info("Voucher soft-deleted: id={}", voucherId);
    }

    @Transactional(readOnly = true)
    public VoucherResponse getById(UUID voucherId) {
        return voucherMapper.toResponse(findByIdOrThrow(voucherId));
    }

    @Transactional(readOnly = true)
    public VoucherResponse getByCode(String code) {
        return voucherMapper.toResponse(findByCodeOrThrow(code));
    }

    @Transactional(readOnly = true)
    public PagedResponse<VoucherResponse> getVouchers(VoucherFilter filter, Pageable pageable) {
        Page<Voucher> page = voucherRepository.findAll(
                VoucherSpecification.withFilter(filter), pageable);
        return PagedResponse.of(page.map(voucherMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PagedResponse<VoucherUsageResponse> getUsages(UUID voucherId, Pageable pageable) {
        if (!voucherRepository.existsByIdAndDeletedFalse(voucherId)) {
            throw new AppException(ErrorCode.VOUCHER_NOT_FOUND);
        }
        Page<VoucherUsage> page = voucherUsageRepository.findByVoucherId(voucherId, pageable);
        return PagedResponse.of(page.map(voucherMapper::toUsageResponse));
    }

    // ─── Customer: validate voucher (preview discount) ────────────────────────

    /**
     * Validates a voucher code and returns a discount preview.
     * Does NOT record usage — use {@link #applyVoucher} for that.
     *
     * @throws AppException with a specific error code if the voucher fails any check
     */
    @Transactional(readOnly = true)
    public ValidateVoucherResponse validateVoucher(String code, Customer customer,
                                                    ValidateVoucherRequest request) {
        Voucher voucher = findByCodeWithRulesOrThrow(code);
        doValidate(voucher, customer, request.getOrderAmount(),
                safeList(request.getProductIds()),
                safeList(request.getCategoryIds()),
                safeList(request.getBrandIds()));

        BigDecimal discount = calculateDiscount(voucher.getPromotion(), request.getOrderAmount());
        BigDecimal finalAmount = request.getOrderAmount().subtract(discount).max(BigDecimal.ZERO);

        return ValidateVoucherResponse.builder()
                .voucherCode(voucher.getCode())
                .promotionName(voucher.getPromotion().getName())
                .discountType(voucher.getPromotion().getDiscountType().name())
                .discountValue(voucher.getPromotion().getDiscountValue())
                .discountAmount(discount)
                .orderAmount(request.getOrderAmount())
                .finalAmount(finalAmount)
                .build();
    }

    // ─── Internal: apply voucher when order is created ────────────────────────

    /**
     * Validates and records the voucher usage for a confirmed order.
     * Called by {@code OrderService} during order creation.
     *
     * <p>Idempotent: if a usage record already exists for this voucher + order, the
     * existing discount amount is returned without creating a duplicate.
     *
     * @param voucherCode   customer-supplied voucher code; if null/blank, returns ZERO
     * @param customer      the placing customer
     * @param orderId       the newly created order's ID
     * @param orderAmount   order subtotal (before discount)
     * @param productIds    product IDs in the order
     * @param categoryIds   category IDs of order items
     * @param brandIds      brand IDs of order items
     * @return the discount amount to subtract from the order total
     */
    @Transactional
    public BigDecimal applyVoucher(String voucherCode, Customer customer, UUID orderId,
                                   BigDecimal orderAmount, List<UUID> productIds,
                                   List<UUID> categoryIds, List<UUID> brandIds) {
        if (voucherCode == null || voucherCode.isBlank()) {
            return BigDecimal.ZERO;
        }

        // Eager-load promotion + rules to avoid N+1 during doValidate()
        Voucher voucher = findByCodeWithRulesOrThrow(voucherCode);

        // Idempotent: usage already recorded for this order
        if (voucherUsageRepository.existsByVoucherIdAndOrderId(voucher.getId(), orderId)) {
            log.warn("Voucher usage already recorded: voucherId={} orderId={}",
                    voucher.getId(), orderId);
            return BigDecimal.ZERO;
        }

        doValidate(voucher, customer, orderAmount,
                safeList(productIds), safeList(categoryIds), safeList(brandIds));

        BigDecimal discount = calculateDiscount(voucher.getPromotion(), orderAmount);

        // Record usage
        VoucherUsage usage = new VoucherUsage();
        usage.setVoucher(voucher);
        usage.setCustomer(customer);
        usage.setOrderId(orderId);
        usage.setDiscountAmount(discount);
        voucherUsageRepository.save(usage);

        // Atomically increment counters — no read-modify-write race
        voucherRepository.incrementUsageCount(voucher.getId());
        promotionService.incrementUsageCount(voucher.getPromotion().getId());

        log.info("Voucher applied: code={} orderId={} discount={}", voucherCode, orderId, discount);
        return discount;
    }

    /**
     * Releases a voucher usage when an order is cancelled.
     * Decrements counters but does NOT delete the usage record (immutable audit trail).
     */
    @Transactional
    public void releaseVoucher(String voucherCode, UUID orderId) {
        if (voucherCode == null || voucherCode.isBlank()) {
            return;
        }

        Voucher voucher = voucherRepository.findByCodeIgnoreCase(voucherCode).orElse(null);
        if (voucher == null) {
            log.warn("releaseVoucher: voucher not found for code={}", voucherCode);
            return;
        }

        if (!voucherUsageRepository.existsByVoucherIdAndOrderId(voucher.getId(), orderId)) {
            return;
        }

        // Atomically decrement counters — floored at zero by repository query
        voucherRepository.decrementUsageCount(voucher.getId());
        promotionService.decrementUsageCount(voucher.getPromotion().getId());

        log.info("Voucher usage released: code={} orderId={}", voucherCode, orderId);
    }

    // ─── Validation logic ─────────────────────────────────────────────────────

    /**
     * Runs all validity checks for a voucher in the given order context.
     * Throws a specific {@link AppException} on the first failing check.
     */
    private void doValidate(Voucher voucher, Customer customer, BigDecimal orderAmount,
                             List<UUID> productIds, List<UUID> categoryIds, List<UUID> brandIds) {
        LocalDateTime now = LocalDateTime.now();

        // 1. Active flag
        if (!voucher.isActive()) {
            throw new AppException(ErrorCode.VOUCHER_INVALID, "Voucher is inactive");
        }

        // 2. Validity window
        if (now.isBefore(voucher.getStartDate()) || now.isAfter(voucher.getEndDate())) {
            throw new AppException(ErrorCode.VOUCHER_EXPIRED);
        }

        // 3. Total usage limit
        if (voucher.getUsageLimit() != null && voucher.getUsageCount() >= voucher.getUsageLimit()) {
            throw new AppException(ErrorCode.VOUCHER_USAGE_LIMIT_EXCEEDED);
        }

        // 4. Per-user usage limit
        if (voucher.getUsageLimitPerUser() != null) {
            long userUsages = voucherUsageRepository
                    .countByVoucherIdAndCustomerId(voucher.getId(), customer.getId());
            if (userUsages >= voucher.getUsageLimitPerUser()) {
                throw new AppException(ErrorCode.VOUCHER_USER_LIMIT_EXCEEDED);
            }
        }

        Promotion promotion = voucher.getPromotion();

        // 5. Promotion active flag
        if (!promotion.isActive()) {
            throw new AppException(ErrorCode.VOUCHER_INVALID, "Linked promotion is inactive");
        }

        // 6. Promotion validity window
        if (now.isBefore(promotion.getStartDate()) || now.isAfter(promotion.getEndDate())) {
            throw new AppException(ErrorCode.VOUCHER_EXPIRED,
                    "Linked promotion has expired or not yet started");
        }

        // 7. Promotion total usage limit
        if (promotion.getUsageLimit() != null
                && promotion.getUsageCount() >= promotion.getUsageLimit()) {
            throw new AppException(ErrorCode.VOUCHER_USAGE_LIMIT_EXCEEDED,
                    "Promotion usage limit has been reached");
        }

        // 8. Minimum order amount (from promotion)
        if (promotion.getMinimumOrderAmount() != null
                && orderAmount.compareTo(promotion.getMinimumOrderAmount()) < 0) {
            throw new AppException(ErrorCode.VOUCHER_MIN_ORDER_NOT_MET,
                    "Minimum order amount is " + promotion.getMinimumOrderAmount());
        }

        // 9. Promotion rules
        for (PromotionRule rule : promotion.getRules()) {
            evaluateRule(rule, customer, orderAmount, productIds, categoryIds, brandIds);
        }
    }

    /**
     * Evaluates a single promotion rule. Throws {@link AppException} if the rule is not satisfied.
     */
    private void evaluateRule(PromotionRule rule, Customer customer, BigDecimal orderAmount,
                               List<UUID> productIds, List<UUID> categoryIds, List<UUID> brandIds) {
        String value = rule.getRuleValue().trim();

        switch (rule.getRuleType()) {
            case MIN_ORDER_AMOUNT -> {
                BigDecimal minAmount = new BigDecimal(value);
                if (orderAmount.compareTo(minAmount) < 0) {
                    throw new AppException(ErrorCode.VOUCHER_MIN_ORDER_NOT_MET,
                            "Minimum order amount required: " + minAmount);
                }
            }
            case SPECIFIC_PRODUCTS -> {
                List<UUID> requiredIds = parseIds(value);
                boolean hasMatch = productIds.stream().anyMatch(requiredIds::contains);
                if (!hasMatch) {
                    throw new AppException(ErrorCode.VOUCHER_NOT_APPLICABLE,
                            "Order does not contain required products");
                }
            }
            case SPECIFIC_CATEGORIES -> {
                List<UUID> requiredIds = parseIds(value);
                boolean hasMatch = categoryIds.stream().anyMatch(requiredIds::contains);
                if (!hasMatch) {
                    throw new AppException(ErrorCode.VOUCHER_NOT_APPLICABLE,
                            "Order does not contain items from required categories");
                }
            }
            case SPECIFIC_BRANDS -> {
                List<UUID> requiredIds = parseIds(value);
                boolean hasMatch = brandIds.stream().anyMatch(requiredIds::contains);
                if (!hasMatch) {
                    throw new AppException(ErrorCode.VOUCHER_NOT_APPLICABLE,
                            "Order does not contain items from required brands");
                }
            }
            case FIRST_ORDER -> {
                long completedOrders = orderRepository.countCompletedByCustomerId(customer.getId());
                if (completedOrders > 0) {
                    throw new AppException(ErrorCode.VOUCHER_NOT_APPLICABLE,
                            "Voucher is only valid for first-time orders");
                }
            }
        }
    }

    // ─── Discount calculation ─────────────────────────────────────────────────

    /**
     * Calculates the monetary discount for the given order amount based on the promotion config.
     * For PERCENTAGE: applies the rate and respects {@code maxDiscountAmount} cap.
     * For FIXED_AMOUNT: returns the fixed value, capped at orderAmount.
     */
    private BigDecimal calculateDiscount(Promotion promotion, BigDecimal orderAmount) {
        if (promotion.getDiscountType() == DiscountType.PERCENTAGE) {
            // discountValue is a percentage e.g. 20 means 20%
            BigDecimal rate = promotion.getDiscountValue()
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            BigDecimal discount = orderAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);

            if (promotion.getMaxDiscountAmount() != null) {
                discount = discount.min(promotion.getMaxDiscountAmount());
            }
            return discount;
        } else {
            // FIXED_AMOUNT — cap at order amount so total never goes negative
            return promotion.getDiscountValue().min(orderAmount);
        }
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private Voucher findByIdOrThrow(UUID id) {
        return voucherRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
    }

    private Voucher findByCodeOrThrow(String code) {
        return voucherRepository.findByCodeIgnoreCaseAndDeletedFalse(code)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
    }

    /** Finds a voucher with its promotion and rules eagerly loaded (avoids N+1 in doValidate). */
    private Voucher findByCodeWithRulesOrThrow(String code) {
        return voucherRepository.findByCodeWithRules(code)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
    }

    private String resolveCode(String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim().toUpperCase();
        }
        return CodeGenerator.generateVoucherCode();
    }

    private List<UUID> parseIds(String commaSeparated) {
        try {
            return Arrays.stream(commaSeparated.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(UUID::fromString)
                    .toList();
        } catch (IllegalArgumentException e) {
            // A malformed rule value is a data integrity problem, not a user error.
            // Silently returning empty would bypass product/category/brand restrictions.
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Malformed promotion rule value: " + commaSeparated);
        }
    }

    private <T> List<T> safeList(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }

    private void validateDateRange(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "End date must be after start date");
        }
    }
}
