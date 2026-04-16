package com.locnguyen.ecommerce.domains.promotion.entity;

import com.locnguyen.ecommerce.common.auditing.SoftDeleteEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A redeemable voucher code that links to a {@link Promotion}.
 *
 * <p>The voucher carries its own validity window and per-user usage cap,
 * allowing fine-grained control independent of the base promotion settings.
 * Discount logic (type, value, scope, rules) is always read from the linked promotion.
 *
 * <p>Soft-deleted so usage history stays queryable.
 */
@Entity
@Table(name = "vouchers")
@Getter
@Setter
@NoArgsConstructor
public class Voucher extends SoftDeleteEntity {

    /**
     * JPA optimistic lock version — prevents concurrent {@code applyVoucher} calls
     * from bypassing the usage limit without a database-level lock.
     * The column must exist in the DDL; added in V11 migration.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    /** User-facing code, e.g. {@code SUMMER20}. Unique, case-insensitive by convention. */
    @Column(name = "code", length = 100, nullable = false, unique = true)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotion promotion;

    /** Null means no total-usage limit for this voucher. */
    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "usage_count", nullable = false)
    private int usageCount = 0;

    /** Null means a single customer may use it any number of times. */
    @Column(name = "usage_limit_per_user")
    private Integer usageLimitPerUser;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
