package com.locnguyen.ecommerce.domains.payment.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.payment.enums.RefundStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Records a single refund request against a {@link Payment}.
 *
 * <p>A payment may accumulate multiple partial refunds until the total
 * refunded amount reaches the original payment amount, at which point
 * the payment status transitions to REFUNDED.
 *
 * <p>Extends {@link BaseEntity} for a full audit trail (created_by / updated_by).
 */
@Entity
@Table(name = "payment_refunds",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_payment_refunds_code", columnNames = "refund_code")
        }
)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "payment")
public class PaymentRefund extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "refund_code", length = 50, nullable = false, unique = true)
    private String refundCode;

    @Column(name = "amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private RefundStatus status = RefundStatus.PENDING;

    @Column(name = "provider_refund_id", length = 200)
    private String providerRefundId;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "requested_by", length = 255)
    private String requestedBy;

    @Column(name = "note", length = 500)
    private String note;
}
