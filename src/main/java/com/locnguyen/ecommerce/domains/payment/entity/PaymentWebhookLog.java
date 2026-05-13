package com.locnguyen.ecommerce.domains.payment.entity;

import com.locnguyen.ecommerce.domains.payment.enums.WebhookLogStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable audit log for every inbound gateway webhook.
 *
 * <p>Written before any processing begins so that even crashes or
 * signature failures leave a trace. Does not extend {@link com.locnguyen.ecommerce.common.auditing.BaseEntity}
 * because it manages its own minimal timestamps — the table has no
 * created_by / updated_by columns.
 */
@Entity
@Table(name = "payment_webhook_logs")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "payment")
public class PaymentWebhookLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "provider", length = 100, nullable = false)
    private String provider;

    @Column(name = "order_code", length = 50)
    private String orderCode;

    @Column(name = "provider_txn_id", length = 200)
    private String providerTxnId;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "signature", length = 500)
    private String signature;

    @Column(name = "signature_valid")
    private Boolean signatureValid;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private WebhookLogStatus status = WebhookLogStatus.RECEIVED;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
