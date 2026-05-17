package com.locnguyen.ecommerce.domains.shipment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "carrier_webhook_logs")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "shipment")
public class CarrierWebhookLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id")
    private Shipment shipment;

    @Column(name = "carrier_code", length = 100, nullable = false)
    private String carrierCode;

    @Column(name = "provider_order_id", length = 200)
    private String providerOrderId;

    @Column(name = "tracking_number", length = 200)
    private String trackingNumber;

    @Column(name = "event_type", length = 100, nullable = false)
    private String eventType;

    @Column(name = "event_key", length = 128, nullable = false, unique = true)
    private String eventKey;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "headers", columnDefinition = "TEXT")
    private String headers;

    @Column(name = "signature_valid")
    private Boolean signatureValid;

    @Column(name = "processed", nullable = false)
    private boolean processed;

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
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
