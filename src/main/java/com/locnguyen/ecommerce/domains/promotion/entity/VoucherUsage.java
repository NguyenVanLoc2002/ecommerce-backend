package com.locnguyen.ecommerce.domains.promotion.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

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
@Table(name = "voucher_usages", uniqueConstraints = @UniqueConstraint(
        name = "uk_voucher_usages_voucher_order", columnNames = {"voucher_id", "order_id"}))
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
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID orderId;

    @Column(name = "discount_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal discountAmount;
}
