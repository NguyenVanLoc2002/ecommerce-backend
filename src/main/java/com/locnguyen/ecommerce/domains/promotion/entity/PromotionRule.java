package com.locnguyen.ecommerce.domains.promotion.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.promotion.enums.RuleType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single condition that must pass for the parent {@link Promotion} to apply.
 *
 * <p>All rules on a promotion are AND-combined — every rule must pass.
 *
 * <p>Rule value encoding per {@link RuleType}:
 * <ul>
 *   <li>{@code MIN_ORDER_AMOUNT}    — decimal string, e.g. {@code "200000.00"}</li>
 *   <li>{@code SPECIFIC_PRODUCTS}   — comma-separated product IDs</li>
 *   <li>{@code SPECIFIC_CATEGORIES} — comma-separated category IDs</li>
 *   <li>{@code SPECIFIC_BRANDS}     — comma-separated brand IDs</li>
 *   <li>{@code FIRST_ORDER}         — {@code "true"}</li>
 * </ul>
 */
@Entity
@Table(name = "promotion_rules")
@Getter
@Setter
@NoArgsConstructor
public class PromotionRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotion promotion;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", length = 100, nullable = false)
    private RuleType ruleType;

    @Column(name = "rule_value", length = 500, nullable = false)
    private String ruleValue;

    @Column(name = "description", length = 255)
    private String description;
}
