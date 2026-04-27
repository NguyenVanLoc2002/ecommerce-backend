package com.locnguyen.ecommerce.domains.payment.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.payment.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Immutable audit trail for every payment attempt or state change.
 *
 * <p>Each payment can have multiple transactions (e.g., INITIATED → FAILED → INITIATED → SUCCESS).
 * Records provider-specific data for gateway reconciliation.
 *
 * <p>Extends {@link BaseEntity} with overridden updatedBy (no auto-update — transactions are immutable).
 * Set {@code updatable = false} on updated_by to prevent accidental modification.
 *
 * <p>Actually extends BaseEntity normally but should be treated as immutable after creation.
 */
@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "payment")
public class PaymentTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "transaction_code", length = 50, nullable = false, unique = true)
    private String transactionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private TransactionStatus status;

    @Column(name = "amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 50, nullable = false)
    private PaymentMethod method;

    @Column(name = "provider", length = 100)
    private String provider;

    @Column(name = "provider_txn_id", length = 200)
    private String providerTxnId;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "note", length = 500)
    private String note;
}
