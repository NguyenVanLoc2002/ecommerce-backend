package com.locnguyen.ecommerce.domains.promotion.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VoucherService}.
 *
 * All dependencies are mocked — no Spring context, no database.
 * Tests cover discount calculation, all validation gates,
 * apply/release lifecycle, and admin CRUD edge cases.
 */
@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock VoucherRepository voucherRepository;
    @Mock VoucherUsageRepository voucherUsageRepository;
    @Mock VoucherMapper voucherMapper;
    @Mock PromotionService promotionService;
    @Mock OrderRepository orderRepository;

    @InjectMocks VoucherService voucherService;

    // ─── time constants ──────────────────────────────────────────────────────

    static final LocalDateTime PAST   = LocalDateTime.now().minusDays(30);
    static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(30);

    // ─── entity factories ─────────────────────────────────────────────────────

    /** Creates an active promotion with no usage limit and no rules. */
    private Promotion promotion(DiscountType type, BigDecimal value) {
        Promotion p = new Promotion();
        setId(p, 1L);
        p.setName("Test Promo");
        p.setDiscountType(type);
        p.setDiscountValue(value);
        p.setMinimumOrderAmount(BigDecimal.ZERO);
        p.setMaxDiscountAmount(null);
        p.setStartDate(PAST);
        p.setEndDate(FUTURE);
        p.setActive(true);
        return p;
    }

    /** Creates an active voucher with no usage limit. */
    private Voucher voucher(Promotion promo) {
        Voucher v = new Voucher();
        setId(v, 10L);
        v.setCode("TESTCODE");
        v.setPromotion(promo);
        v.setStartDate(PAST);
        v.setEndDate(FUTURE);
        v.setActive(true);
        v.setUsageCount(0);
        return v;
    }

    private PromotionRule rule(Promotion promo, RuleType type, String value) {
        PromotionRule r = new PromotionRule();
        r.setPromotion(promo);
        r.setRuleType(type);
        r.setRuleValue(value);
        return r;
    }

    private Customer customer(long id) {
        Customer c = mock(Customer.class);
        when(c.getId()).thenReturn(id);
        return c;
    }

    private ValidateVoucherRequest validateRequest(String amount) {
        ValidateVoucherRequest r = new ValidateVoucherRequest();
        r.setOrderAmount(new BigDecimal(amount));
        return r;
    }

    private CreateVoucherRequest createRequest(String code, Long promotionId) {
        CreateVoucherRequest r = new CreateVoucherRequest();
        r.setCode(code);
        r.setPromotionId(promotionId);
        r.setStartDate(PAST);
        r.setEndDate(FUTURE);
        return r;
    }

    /** Uses Spring's ReflectionTestUtils to set the private id field inherited from BaseEntity. */
    private static void setId(Object entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }

    // ─── Discount calculation (tested through validateVoucher) ────────────────

    @Nested
    class DiscountCalculation {

        @Test
        void percentage_basic_calculates_correctly() {
            Promotion promo = promotion(DiscountType.PERCENTAGE, BigDecimal.valueOf(20));
            Voucher v = voucher(promo);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));
            when(voucherUsageRepository.countByVoucherIdAndCustomerId(anyLong(), anyLong()))
                    .thenReturn(0L);

            ValidateVoucherResponse resp = voucherService.validateVoucher(
                    "TESTCODE", customer(1), validateRequest("100000"));

            assertThat(resp.getDiscountAmount()).isEqualByComparingTo("20000.00");
            assertThat(resp.getFinalAmount()).isEqualByComparingTo("80000.00");
        }

        @Test
        void percentage_capped_by_maxDiscountAmount() {
            Promotion promo = promotion(DiscountType.PERCENTAGE, BigDecimal.valueOf(50));
            promo.setMaxDiscountAmount(new BigDecimal("100000.00")); // 50% of 1,000,000 = 500,000 → capped
            Voucher v = voucher(promo);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));
            when(voucherUsageRepository.countByVoucherIdAndCustomerId(anyLong(), anyLong()))
                    .thenReturn(0L);

            ValidateVoucherResponse resp = voucherService.validateVoucher(
                    "TESTCODE", customer(1), validateRequest("1000000"));

            assertThat(resp.getDiscountAmount()).isEqualByComparingTo("100000.00");
        }

        @Test
        void percentage_below_cap_applies_exact_percentage() {
            Promotion promo = promotion(DiscountType.PERCENTAGE, BigDecimal.valueOf(10));
            promo.setMaxDiscountAmount(new BigDecimal("500000.00")); // 10% of 100,000 = 10,000 < cap
            Voucher v = voucher(promo);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));
            when(voucherUsageRepository.countByVoucherIdAndCustomerId(anyLong(), anyLong()))
                    .thenReturn(0L);

            ValidateVoucherResponse resp = voucherService.validateVoucher(
                    "TESTCODE", customer(1), validateRequest("100000"));

            assertThat(resp.getDiscountAmount()).isEqualByComparingTo("10000.00");
        }

        @Test
        void fixedAmount_basic_deducted_from_order() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, new BigDecimal("50000"));
            Voucher v = voucher(promo);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));
            when(voucherUsageRepository.countByVoucherIdAndCustomerId(anyLong(), anyLong()))
                    .thenReturn(0L);

            ValidateVoucherResponse resp = voucherService.validateVoucher(
                    "TESTCODE", customer(1), validateRequest("200000"));

            assertThat(resp.getDiscountAmount()).isEqualByComparingTo("50000.00");
            assertThat(resp.getFinalAmount()).isEqualByComparingTo("150000.00");
        }

        @Test
        void fixedAmount_capped_at_orderAmount_so_total_never_goes_negative() {
            // Voucher discount is larger than the order — final amount must be 0, not negative
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, new BigDecimal("300000"));
            Voucher v = voucher(promo);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));
            when(voucherUsageRepository.countByVoucherIdAndCustomerId(anyLong(), anyLong()))
                    .thenReturn(0L);

            ValidateVoucherResponse resp = voucherService.validateVoucher(
                    "TESTCODE", customer(1), validateRequest("100000"));

            assertThat(resp.getDiscountAmount()).isEqualByComparingTo("100000.00");
            assertThat(resp.getFinalAmount()).isEqualByComparingTo("0.00");
        }
    }

    // ─── validateVoucher — voucher-level checks ───────────────────────────────

    @Nested
    class VoucherLevelValidation {

        @Test
        void throws_NOT_FOUND_when_code_absent_from_db() {
            when(voucherRepository.findByCodeWithRules("GHOST")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    voucherService.validateVoucher("GHOST", customer(1), validateRequest("100")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_NOT_FOUND);
        }

        @Test
        void throws_INVALID_when_voucher_active_flag_is_false() {
            Voucher v = voucher(promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN));
            v.setActive(false);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));

            assertThatThrownBy(() ->
                    voucherService.validateVoucher("TESTCODE", customer(1), validateRequest("100")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_INVALID);
        }

        @Test
        void throws_EXPIRED_when_voucher_start_is_in_the_future() {
            Voucher v = voucher(promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN));
            v.setStartDate(LocalDateTime.now().plusHours(1));
            v.setEndDate(LocalDateTime.now().plusDays(30));
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));

            assertThatThrownBy(() ->
                    voucherService.validateVoucher("TESTCODE", customer(1), validateRequest("100")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_EXPIRED);
        }

        @Test
        void throws_EXPIRED_when_voucher_end_date_has_passed() {
            Voucher v = voucher(promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN));
            v.setStartDate(LocalDateTime.now().minusDays(10));
            v.setEndDate(LocalDateTime.now().minusSeconds(1));
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));

            assertThatThrownBy(() ->
                    voucherService.validateVoucher("TESTCODE", customer(1), validateRequest("100")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_EXPIRED);
        }

        @Test
        void throws_USAGE_LIMIT_EXCEEDED_when_voucher_usage_equals_limit() {
            Voucher v = voucher(promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN));
            v.setUsageLimit(100);
            v.setUsageCount(100); // exactly at limit
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));

            assertThatThrownBy(() ->
                    voucherService.validateVoucher("TESTCODE", customer(1), validateRequest("100")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_USAGE_LIMIT_EXCEEDED);
        }

        @Test
        void passes_when_voucher_usage_is_one_below_limit() {
            Voucher v = voucher(promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN));
            v.setUsageLimit(100);
            v.setUsageCount(99); // one slot remaining
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));
            when(voucherUsageRepository.countByVoucherIdAndCustomerId(anyLong(), anyLong()))
                    .thenReturn(0L);

            assertThatCode(() ->
                    voucherService.validateVoucher("TESTCODE", customer(1), validateRequest("100")))
                    .doesNotThrowAnyException();
        }

        @Test
        void null_usageLimit_means_unlimited_redemptions() {
            Voucher v = voucher(promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN));
            v.setUsageLimit(null);
            v.setUsageCount(999999);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));
            when(voucherUsageRepository.countByVoucherIdAndCustomerId(anyLong(), anyLong()))
                    .thenReturn(0L);

            assertThatCode(() ->
                    voucherService.validateVoucher("TESTCODE", customer(1), validateRequest("100")))
                    .doesNotThrowAnyException();
        }

        @Test
        void throws_USER_LIMIT_EXCEEDED_when_per_user_count_at_limit() {
            Voucher v = voucher(promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN));
            v.setUsageLimitPerUser(3);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));
            when(voucherUsageRepository.countByVoucherIdAndCustomerId(10L, 77L)).thenReturn(3L);

            assertThatThrownBy(() ->
                    voucherService.validateVoucher("TESTCODE", customer(77), validateRequest("100")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_USER_LIMIT_EXCEEDED);
        }

        @Test
        void passes_when_per_user_count_is_below_limit() {
            Voucher v = voucher(promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN));
            v.setUsageLimitPerUser(3);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));
            when(voucherUsageRepository.countByVoucherIdAndCustomerId(10L, 77L)).thenReturn(2L);

            assertThatCode(() ->
                    voucherService.validateVoucher("TESTCODE", customer(77), validateRequest("100")))
                    .doesNotThrowAnyException();
        }
    }

    // ─── validateVoucher — promotion-level checks ─────────────────────────────

    @Nested
    class PromotionLevelValidation {

        @Test
        void throws_INVALID_when_linked_promotion_inactive() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            promo.setActive(false);
            Voucher v = voucher(promo);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));

            assertThatThrownBy(() ->
                    voucherService.validateVoucher("TESTCODE", customer(1), validateRequest("100")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_INVALID);
        }

        @Test
        void throws_EXPIRED_when_promotion_has_not_started_yet() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            promo.setStartDate(LocalDateTime.now().plusDays(1));
            promo.setEndDate(LocalDateTime.now().plusDays(10));
            Voucher v = voucher(promo);
            v.setStartDate(PAST);
            v.setEndDate(FUTURE);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));

            assertThatThrownBy(() ->
                    voucherService.validateVoucher("TESTCODE", customer(1), validateRequest("100")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_EXPIRED);
        }

        @Test
        void throws_EXPIRED_when_promotion_has_ended() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            promo.setStartDate(LocalDateTime.now().minusDays(10));
            promo.setEndDate(LocalDateTime.now().minusSeconds(1));
            Voucher v = voucher(promo);
            v.setStartDate(PAST);
            v.setEndDate(FUTURE);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));

            assertThatThrownBy(() ->
                    voucherService.validateVoucher("TESTCODE", customer(1), validateRequest("100")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_EXPIRED);
        }

        @Test
        void throws_USAGE_LIMIT_EXCEEDED_when_promotion_count_equals_limit() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            promo.setUsageLimit(50);
            promo.setUsageCount(50);
            Voucher v = voucher(promo);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));

            assertThatThrownBy(() ->
                    voucherService.validateVoucher("TESTCODE", customer(1), validateRequest("100")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_USAGE_LIMIT_EXCEEDED);
        }

        @Test
        void throws_MIN_ORDER_NOT_MET_from_minimumOrderAmount_on_promotion() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            promo.setMinimumOrderAmount(new BigDecimal("500000.00"));
            Voucher v = voucher(promo);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));

            assertThatThrownBy(() ->
                    voucherService.validateVoucher("TESTCODE", customer(1), validateRequest("100000")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_MIN_ORDER_NOT_MET);
        }
    }

    // ─── validateVoucher — promotion rule evaluation ──────────────────────────

    @Nested
    class RuleEvaluation {

        @Test
        void minOrderAmount_rule_passes_when_order_meets_threshold() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            promo.setRules(List.of(rule(promo, RuleType.MIN_ORDER_AMOUNT, "200000.00")));
            Voucher v = voucher(promo);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));
            when(voucherUsageRepository.countByVoucherIdAndCustomerId(anyLong(), anyLong()))
                    .thenReturn(0L);

            assertThatCode(() ->
                    voucherService.validateVoucher("TESTCODE", customer(1), validateRequest("200000")))
                    .doesNotThrowAnyException();
        }

        @Test
        void minOrderAmount_rule_fails_when_order_is_below_threshold() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            promo.setRules(List.of(rule(promo, RuleType.MIN_ORDER_AMOUNT, "200000.00")));
            Voucher v = voucher(promo);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));

            assertThatThrownBy(() ->
                    voucherService.validateVoucher("TESTCODE", customer(1), validateRequest("199999")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_MIN_ORDER_NOT_MET);
        }

        @Test
        void specificProducts_rule_passes_when_at_least_one_product_matches() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            promo.setRules(List.of(rule(promo, RuleType.SPECIFIC_PRODUCTS, "5,10,15")));
            Voucher v = voucher(promo);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));
            when(voucherUsageRepository.countByVoucherIdAndCustomerId(anyLong(), anyLong()))
                    .thenReturn(0L);

            ValidateVoucherRequest req = validateRequest("500000");
            req.setProductIds(List.of(10L, 20L, 30L)); // 10 is in required list

            assertThatCode(() -> voucherService.validateVoucher("TESTCODE", customer(1), req))
                    .doesNotThrowAnyException();
        }

        @Test
        void specificProducts_rule_fails_when_no_product_in_order_matches() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            promo.setRules(List.of(rule(promo, RuleType.SPECIFIC_PRODUCTS, "5,10,15")));
            Voucher v = voucher(promo);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));

            ValidateVoucherRequest req = validateRequest("500000");
            req.setProductIds(List.of(99L, 100L)); // none in required list

            assertThatThrownBy(() -> voucherService.validateVoucher("TESTCODE", customer(1), req))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_NOT_APPLICABLE);
        }

        @Test
        void specificProducts_rule_fails_when_productIds_is_null_in_request() {
            // null productIds → safeList → empty → no match against required IDs
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            promo.setRules(List.of(rule(promo, RuleType.SPECIFIC_PRODUCTS, "5,10")));
            Voucher v = voucher(promo);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));

            assertThatThrownBy(() ->
                    voucherService.validateVoucher("TESTCODE", customer(1), validateRequest("300000")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_NOT_APPLICABLE);
        }

        @Test
        void specificCategories_rule_passes_when_category_in_order() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            promo.setRules(List.of(rule(promo, RuleType.SPECIFIC_CATEGORIES, "3,7")));
            Voucher v = voucher(promo);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));
            when(voucherUsageRepository.countByVoucherIdAndCustomerId(anyLong(), anyLong()))
                    .thenReturn(0L);

            ValidateVoucherRequest req = validateRequest("300000");
            req.setCategoryIds(List.of(7L));

            assertThatCode(() -> voucherService.validateVoucher("TESTCODE", customer(1), req))
                    .doesNotThrowAnyException();
        }

        @Test
        void specificBrands_rule_fails_when_required_brand_absent_from_order() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            promo.setRules(List.of(rule(promo, RuleType.SPECIFIC_BRANDS, "2,4")));
            Voucher v = voucher(promo);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));

            ValidateVoucherRequest req = validateRequest("300000");
            req.setBrandIds(List.of(5L, 6L)); // neither 2 nor 4

            assertThatThrownBy(() -> voucherService.validateVoucher("TESTCODE", customer(1), req))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_NOT_APPLICABLE);
        }

        @Test
        void firstOrder_rule_passes_when_customer_has_no_completed_orders() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            promo.setRules(List.of(rule(promo, RuleType.FIRST_ORDER, "true")));
            Voucher v = voucher(promo);
            Customer cust = customer(42L);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));
            when(voucherUsageRepository.countByVoucherIdAndCustomerId(10L, 42L)).thenReturn(0L);
            when(orderRepository.countCompletedByCustomerId(42L)).thenReturn(0L);

            assertThatCode(() ->
                    voucherService.validateVoucher("TESTCODE", cust, validateRequest("300000")))
                    .doesNotThrowAnyException();
        }

        @Test
        void firstOrder_rule_fails_when_customer_has_at_least_one_completed_order() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            promo.setRules(List.of(rule(promo, RuleType.FIRST_ORDER, "true")));
            Voucher v = voucher(promo);
            Customer cust = customer(42L);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));
            when(orderRepository.countCompletedByCustomerId(42L)).thenReturn(1L);

            assertThatThrownBy(() ->
                    voucherService.validateVoucher("TESTCODE", cust, validateRequest("300000")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_NOT_APPLICABLE);
        }

        @Test
        void malformed_rule_value_throws_INTERNAL_SERVER_ERROR() {
            // parseIds() must NOT silently bypass restrictions on data corruption
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            promo.setRules(List.of(rule(promo, RuleType.SPECIFIC_PRODUCTS, "not,valid,ids")));
            Voucher v = voucher(promo);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));

            ValidateVoucherRequest req = validateRequest("300000");
            req.setProductIds(List.of(1L));

            assertThatThrownBy(() ->
                    voucherService.validateVoucher("TESTCODE", customer(1), req))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        @Test
        void multiple_rules_all_AND_combined_fails_when_any_rule_fails() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            // Rule 1 passes (order >= 100,000), Rule 2 fails (product 99 not in order)
            promo.setRules(List.of(
                    rule(promo, RuleType.MIN_ORDER_AMOUNT, "100000.00"),
                    rule(promo, RuleType.SPECIFIC_PRODUCTS, "99")
            ));
            Voucher v = voucher(promo);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));

            ValidateVoucherRequest req = validateRequest("200000");
            req.setProductIds(List.of(1L, 2L)); // 99 not present

            assertThatThrownBy(() -> voucherService.validateVoucher("TESTCODE", customer(1), req))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_NOT_APPLICABLE);
        }
    }

    // ─── applyVoucher ─────────────────────────────────────────────────────────

    @Nested
    class ApplyVoucher {

        @Test
        void returns_zero_when_code_is_null() {
            BigDecimal result = voucherService.applyVoucher(
                    null, customer(1), 100L, BigDecimal.TEN, List.of(), List.of(), List.of());

            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
            verifyNoInteractions(voucherRepository);
        }

        @Test
        void returns_zero_when_code_is_blank() {
            BigDecimal result = voucherService.applyVoucher(
                    "   ", customer(1), 100L, BigDecimal.TEN, List.of(), List.of(), List.of());

            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
            verifyNoInteractions(voucherRepository);
        }

        @Test
        void idempotent_returns_zero_when_usage_already_recorded_for_order() {
            Voucher v = voucher(promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN));
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));
            when(voucherUsageRepository.existsByVoucherIdAndOrderId(10L, 200L)).thenReturn(true);

            BigDecimal result = voucherService.applyVoucher(
                    "TESTCODE", customer(1), 200L, new BigDecimal("500000"), List.of(), List.of(), List.of());

            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
            verify(voucherRepository, never()).incrementUsageCount(anyLong());
            verify(promotionService, never()).incrementUsageCount(anyLong());
        }

        @Test
        void happy_path_records_usage_and_atomically_increments_both_counters() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, new BigDecimal("50000"));
            Voucher v = voucher(promo);
            Customer cust = customer(7L);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));
            when(voucherUsageRepository.existsByVoucherIdAndOrderId(10L, 300L)).thenReturn(false);
            when(voucherUsageRepository.countByVoucherIdAndCustomerId(10L, 7L)).thenReturn(0L);

            BigDecimal result = voucherService.applyVoucher(
                    "TESTCODE", cust, 300L, new BigDecimal("200000"), List.of(), List.of(), List.of());

            assertThat(result).isEqualByComparingTo("50000.00");

            // VoucherUsage record created with correct orderId and discount
            verify(voucherUsageRepository).save(argThat(u ->
                    u.getOrderId().equals(300L)
                    && u.getDiscountAmount().compareTo(new BigDecimal("50000")) == 0));

            // Both counters incremented via atomic @Modifying queries (not read-modify-write)
            verify(voucherRepository).incrementUsageCount(10L);
            verify(promotionService).incrementUsageCount(1L);
        }

        @Test
        void propagates_VOUCHER_INVALID_when_voucher_inactive() {
            Voucher v = voucher(promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN));
            v.setActive(false);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));
            when(voucherUsageRepository.existsByVoucherIdAndOrderId(anyLong(), anyLong()))
                    .thenReturn(false);

            assertThatThrownBy(() ->
                    voucherService.applyVoucher("TESTCODE", customer(1), 400L,
                            new BigDecimal("100000"), List.of(), List.of(), List.of()))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_INVALID);

            // No counter changes on failed apply
            verify(voucherRepository, never()).incrementUsageCount(anyLong());
            verify(promotionService, never()).incrementUsageCount(anyLong());
        }

        @Test
        void discount_on_apply_matches_validateVoucher_preview() {
            Promotion promo = promotion(DiscountType.PERCENTAGE, BigDecimal.valueOf(15));
            Voucher v = voucher(promo);
            Customer cust = customer(5L);
            when(voucherRepository.findByCodeWithRules("TESTCODE")).thenReturn(Optional.of(v));
            when(voucherUsageRepository.existsByVoucherIdAndOrderId(anyLong(), anyLong()))
                    .thenReturn(false);
            when(voucherUsageRepository.countByVoucherIdAndCustomerId(anyLong(), anyLong()))
                    .thenReturn(0L);

            BigDecimal orderAmount = new BigDecimal("400000");
            BigDecimal applied = voucherService.applyVoucher(
                    "TESTCODE", cust, 999L, orderAmount, List.of(), List.of(), List.of());

            // 15% of 400,000 = 60,000
            assertThat(applied).isEqualByComparingTo("60000.00");
        }
    }

    // ─── releaseVoucher ───────────────────────────────────────────────────────

    @Nested
    class ReleaseVoucher {

        @Test
        void no_op_when_code_is_null() {
            voucherService.releaseVoucher(null, 100L);
            verifyNoInteractions(voucherRepository);
        }

        @Test
        void no_op_when_code_is_blank_whitespace() {
            voucherService.releaseVoucher("   ", 100L);
            verifyNoInteractions(voucherRepository);
        }

        @Test
        void no_op_when_voucher_not_found_in_db() {
            when(voucherRepository.findByCodeIgnoreCase("MISSING")).thenReturn(Optional.empty());

            voucherService.releaseVoucher("MISSING", 100L);

            verify(voucherRepository, never()).decrementUsageCount(anyLong());
            verifyNoInteractions(promotionService);
        }

        @Test
        void no_op_when_no_usage_record_exists_for_the_order() {
            Voucher v = voucher(promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN));
            when(voucherRepository.findByCodeIgnoreCase("TESTCODE")).thenReturn(Optional.of(v));
            when(voucherUsageRepository.existsByVoucherIdAndOrderId(10L, 999L)).thenReturn(false);

            voucherService.releaseVoucher("TESTCODE", 999L);

            verify(voucherRepository, never()).decrementUsageCount(anyLong());
            verify(promotionService, never()).decrementUsageCount(anyLong());
        }

        @Test
        void happy_path_atomically_decrements_both_counters() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            Voucher v = voucher(promo);
            when(voucherRepository.findByCodeIgnoreCase("TESTCODE")).thenReturn(Optional.of(v));
            when(voucherUsageRepository.existsByVoucherIdAndOrderId(10L, 500L)).thenReturn(true);

            voucherService.releaseVoucher("TESTCODE", 500L);

            verify(voucherRepository).decrementUsageCount(10L);
            verify(promotionService).decrementUsageCount(1L);
        }
    }

    // ─── createVoucher ────────────────────────────────────────────────────────

    @Nested
    class CreateVoucher {

        @Test
        void throws_NOT_FOUND_when_promotion_does_not_exist() {
            when(promotionService.findByIdOrThrow(999L))
                    .thenThrow(new AppException(ErrorCode.PROMOTION_NOT_FOUND));

            assertThatThrownBy(() -> voucherService.createVoucher(createRequest("PROMO20", 999L)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PROMOTION_NOT_FOUND);
        }

        @Test
        void throws_BAD_REQUEST_when_end_date_is_before_start_date() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            when(promotionService.findByIdOrThrow(1L)).thenReturn(promo);

            CreateVoucherRequest req = createRequest("CODE", 1L);
            req.setStartDate(FUTURE);
            req.setEndDate(PAST); // reversed range

            assertThatThrownBy(() -> voucherService.createVoucher(req))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.BAD_REQUEST);
        }

        @Test
        void throws_CONFLICT_when_code_already_in_use() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            when(promotionService.findByIdOrThrow(1L)).thenReturn(promo);
            when(voucherRepository.existsByCodeIgnoreCase("TAKEN")).thenReturn(true);

            assertThatThrownBy(() -> voucherService.createVoucher(createRequest("TAKEN", 1L)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_CODE_ALREADY_EXISTS);
        }

        @Test
        void saves_voucher_with_code_normalized_to_uppercase() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            when(promotionService.findByIdOrThrow(1L)).thenReturn(promo);
            when(voucherRepository.existsByCodeIgnoreCase("SUMMER20")).thenReturn(false);
            Voucher saved = voucher(promo);
            when(voucherRepository.save(any())).thenReturn(saved);
            when(voucherMapper.toResponse(any())).thenReturn(mock(VoucherResponse.class));

            voucherService.createVoucher(createRequest("summer20", 1L));

            verify(voucherRepository).save(argThat(v -> "SUMMER20".equals(v.getCode())));
        }

        @Test
        void auto_generates_non_blank_code_when_code_is_null() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            when(promotionService.findByIdOrThrow(1L)).thenReturn(promo);
            when(voucherRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);
            Voucher saved = voucher(promo);
            when(voucherRepository.save(any())).thenReturn(saved);
            when(voucherMapper.toResponse(any())).thenReturn(mock(VoucherResponse.class));

            voucherService.createVoucher(createRequest(null, 1L));

            verify(voucherRepository).save(argThat(v -> v.getCode() != null && !v.getCode().isBlank()));
        }

        @Test
        void auto_generates_non_blank_code_when_code_is_empty_string() {
            Promotion promo = promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN);
            when(promotionService.findByIdOrThrow(1L)).thenReturn(promo);
            when(voucherRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);
            Voucher saved = voucher(promo);
            when(voucherRepository.save(any())).thenReturn(saved);
            when(voucherMapper.toResponse(any())).thenReturn(mock(VoucherResponse.class));

            voucherService.createVoucher(createRequest("   ", 1L));

            verify(voucherRepository).save(argThat(v -> v.getCode() != null && !v.getCode().isBlank()));
        }
    }

    // ─── deleteVoucher ────────────────────────────────────────────────────────

    @Nested
    class DeleteVoucher {

        @Test
        void throws_NOT_FOUND_when_voucher_does_not_exist() {
            when(voucherRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> voucherService.deleteVoucher(99L))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_NOT_FOUND);
        }

        @Test
        void soft_deletes_voucher_without_removing_from_db() {
            Voucher v = voucher(promotion(DiscountType.FIXED_AMOUNT, BigDecimal.TEN));
            when(voucherRepository.findById(10L)).thenReturn(Optional.of(v));
            when(voucherRepository.save(any())).thenReturn(v);

            voucherService.deleteVoucher(10L);

            verify(voucherRepository).save(argThat(Voucher::isDeleted));
        }
    }
}
