package com.locnguyen.ecommerce.domains.shipment.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.carrier.entity.Carrier;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import com.locnguyen.ecommerce.domains.shipment.enums.BookingAttemptStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shipment for an order, currently one shipment per order.
 */
@Entity
@Table(name = "shipments")
@Getter
@Setter
@NoArgsConstructor
public class Shipment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "shipment_code", length = 50, nullable = false, unique = true)
    private String shipmentCode;

    @Column(name = "carrier", length = 100, nullable = false)
    private String carrier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrier_id")
    private Carrier carrierEntity;

    @Column(name = "carrier_shipment_id", length = 200)
    private String carrierShipmentId;

    @Column(name = "tracking_number", length = 200)
    private String trackingNumber;

    @Column(name = "provider_status", length = 100)
    private String providerStatus;

    @Column(name = "provider_tracking_url", length = 500)
    private String providerTrackingUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private ShipmentStatus status = ShipmentStatus.PENDING;

    @Column(name = "estimated_delivery_date")
    private LocalDate estimatedDeliveryDate;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "shipping_fee", precision = 18, scale = 2, nullable = false)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_status", length = 30)
    private BookingAttemptStatus bookingStatus;

    @Column(name = "booking_attempt_key", length = 100)
    private String bookingAttemptKey;

    @Column(name = "carrier_cost", precision = 18, scale = 2)
    private BigDecimal carrierCost;

    @Column(name = "estimated_carrier_cost", precision = 18, scale = 2)
    private BigDecimal estimatedCarrierCost;

    @Column(name = "override_reason_code", length = 50)
    private String overrideReasonCode;

    @Column(name = "override_note", length = 500)
    private String overrideNote;

    @Column(name = "override_actor", length = 100)
    private String overrideActor;

    @Column(name = "override_from_carrier_id", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID overrideFromCarrierId;

    @Column(name = "override_to_carrier_id", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID overrideToCarrierId;

    @Column(name = "override_at")
    private LocalDateTime overrideAt;

    @Column(name = "note", length = 500)
    private String note;

    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = false,
            fetch = FetchType.LAZY)
    @OrderBy("eventTime ASC")
    private List<ShipmentEvent> events = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
