package com.locnguyen.ecommerce.domains.admin.dto;

import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.UUID;
/**
 * Enriched order list item for the admin dashboard.
 * Includes customer identity fields that are not exposed on the customer-facing list.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminOrderListItemResponse {

    private UUID id;
    private String orderCode;

    private UUID customerId;
    /** Order-time identity; null when this is a legacy order without a snapshot. */
    private String customerNameSnapshot;
    private String customerEmailSnapshot;
    private boolean customerSnapshotAvailable;
    /** Current profile summary, present only when the customer profile is active. */
    private String customerName;
    private String customerEmail;
    private boolean currentCustomerProfileAvailable;

    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private ShipmentStatus shipmentStatus;
    private String carrier;
    private String trackingNumber;
    private String attention;
    private String attentionReason;

    private int totalItems;
    private BigDecimal totalAmount;

    private LocalDateTime createdAt;
}
