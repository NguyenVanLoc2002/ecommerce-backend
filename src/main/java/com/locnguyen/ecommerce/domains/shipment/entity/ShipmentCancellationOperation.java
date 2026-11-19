package com.locnguyen.ecommerce.domains.shipment.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.shipment.enums.CancellationOperationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_cancellation_operations")
@Getter
@Setter
@NoArgsConstructor
public class ShipmentCancellationOperation extends BaseEntity {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "order_id", nullable = false, unique = true, length = 36)
    private UUID orderId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", unique = true)
    private Shipment shipment;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 36)
    private String idempotencyKey;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CancellationOperationStatus status = CancellationOperationStatus.PENDING;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
