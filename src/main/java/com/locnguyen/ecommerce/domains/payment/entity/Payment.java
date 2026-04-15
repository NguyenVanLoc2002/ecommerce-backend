package com.locnguyen.ecommerce.domains.payment.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.payment.enums.PaymentRecordStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Payment record — one per order.
 *
 * <p>Tracks the overall payment state. The {@link Order} entity retains
 * denormalized copies of payment_method/payment_status/paid_at for fast
 * querying; this table holds the authoritative full record.
 *
 * <p>Extends {@link BaseEntity} (no soft delete) — payments are permanent records.
 */
@Entity
@Table(name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_payments_order_id", columnNames = "order_id"),
                @UniqueConstraint(name = "uq_payments_code", columnNames = "payment_code")
        }
)
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Payment extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    @ToString.Exclude
    private Order order;

    @Column(name = "payment_code", length = 50, nullable = false, unique = true)
    private String paymentCode;

    @Column(name = "method", length = 50, nullable = false)
    private String method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private PaymentRecordStatus status = PaymentRecordStatus.PENDING;

    @Column(name = "amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = false)
    @ToString.Exclude
    private List<PaymentTransaction> transactions = new ArrayList<>();
}
