package com.locnguyen.ecommerce.domains.promotion.entity;

import com.locnguyen.ecommerce.common.auditing.SoftDeleteEntity;
import com.locnguyen.ecommerce.domains.promotion.enums.DiscountType;
import com.locnguyen.ecommerce.domains.promotion.enums.PromotionScope;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A promotion defines the discount configuration.
 * One promotion may be redeemed through multiple {@link Voucher} codes.
 *
 * <p>Soft-deleted promotions remain in the database for historical voucher records.
 */
@Entity
@Table(name = "promotions")
@Getter
@Setter
@NoArgsConstructor
public class Promotion extends SoftDeleteEntity {

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", length = 50, nullable = false)
    private DiscountType discountType;

    /** Percentage value (0–100) for PERCENTAGE type; flat amount for FIXED_AMOUNT. */
    @Column(name = "discount_value", precision = 18, scale = 2, nullable = false)
    private BigDecimal discountValue;

    /** Caps the final discount for PERCENTAGE type. Null means no cap. */
    @Column(name = "max_discount_amount", precision = 18, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "minimum_order_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal minimumOrderAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", length = 50, nullable = false)
    private PromotionScope scope = PromotionScope.ORDER;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** Null means no limit on total usages across all vouchers. */
    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "usage_count", nullable = false)
    private int usageCount = 0;

    @OneToMany(mappedBy = "promotion", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    private List<PromotionRule> rules = new ArrayList<>();
}
