package com.locnguyen.ecommerce.domains.promotion.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Immutable record of a voucher redemption.
 *
 * <p>Never updated or deleted — serves as an audit trail and the source of truth
 * for per-user and total usage counts.
 *
 * <p>{@code orderId} is stored as a plain column (not a FK) to avoid a circular
 * dependency between the promotion and order modules.
 */
@Entity
@Table(name = "voucher_usages")
@Getter
@Setter
@NoArgsConstructor
public class VoucherUsage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** Plain reference — not a FK so we avoid a circular dependency with the order module. */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "discount_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal discountAmount;
}
