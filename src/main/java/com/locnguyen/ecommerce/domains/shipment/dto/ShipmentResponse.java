package com.locnguyen.ecommerce.domains.shipment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Shipment details and tracking timeline")
public class ShipmentResponse {

    private final Long id;
    private final Long orderId;
    private final String orderCode;
    private final String shipmentCode;
    private final String carrier;
    private final String trackingNumber;
    private final ShipmentStatus status;
    private final LocalDate estimatedDeliveryDate;
    private final LocalDateTime deliveredAt;
    private final BigDecimal shippingFee;
    private final String note;

    /** Chronological tracking events — populated for detail views; null in list views. */
    private final List<ShipmentEventResponse> events;

    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
