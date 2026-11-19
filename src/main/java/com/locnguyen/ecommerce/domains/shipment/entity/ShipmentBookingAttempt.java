package com.locnguyen.ecommerce.domains.shipment.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.shipment.enums.BookingAttemptStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "shipment_booking_attempts")
@Getter
@Setter
@NoArgsConstructor
public class ShipmentBookingAttempt extends BaseEntity {

    @OneToOne(optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, unique = true)
    private Shipment shipment;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 50)
    private CarrierProviderType providerType;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BookingAttemptStatus status = BookingAttemptStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 1;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
