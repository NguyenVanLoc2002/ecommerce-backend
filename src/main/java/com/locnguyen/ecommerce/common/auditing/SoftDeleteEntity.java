package com.locnguyen.ecommerce.common.auditing;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * Extension of {@link BaseEntity} with soft-delete support.
 *
 * <p>The {@code @SQLRestriction} ensures all JPA queries automatically filter out
 * deleted records ({@code is_deleted = false}). Hard deletes are forbidden for
 * entities using this base — use {@link #softDelete(String)} instead.
 *
 * <p>Apply to: users, customers, products, variants, categories, brands, vouchers.
 * Do NOT apply to: payment_transactions, stock_movements, audit_logs (immutable logs).
 */
@MappedSuperclass
@SQLRestriction("is_deleted = false")
@Getter
@Setter
public abstract class SoftDeleteEntity extends BaseEntity {

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    public void softDelete(String actor) {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = actor;
    }

    public boolean isActive() {
        return !deleted;
    }
}
