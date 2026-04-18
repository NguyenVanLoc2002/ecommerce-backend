package com.locnguyen.ecommerce.domains.inventory.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.inventory.enums.ReservationStatus;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Temporarily holds stock for an order (or other reference) during checkout/payment.
 *
 * <p>Lifecycle: PENDING → RELEASED (on cancel/complete) / EXPIRED (auto-release) / CANCELLED.
 *
 * <p>Extends {@link BaseEntity} (no soft delete) — reservations are immutable audit records.
 */
@Entity
@Table(name = "inventory_reservations")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "variant")
public class InventoryReservation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private ReservationStatus status = ReservationStatus.PENDING;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
