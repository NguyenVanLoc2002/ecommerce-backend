package com.locnguyen.ecommerce.domains.shipment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Shipment details and tracking timeline")
public class ShipmentResponse {

    private final UUID id;
    private final UUID orderId;
    private final String orderCode;
    private final String shipmentCode;
    private final UUID carrierId;
    private final String carrierCode;
    private final CarrierProviderType carrierProviderType;
    private final String carrier;
    private final String carrierShipmentId;
    private final String trackingNumber;
    private final String providerStatus;
    private final String providerTrackingUrl;
    private final ShipmentStatus status;
    private final LocalDate estimatedDeliveryDate;
    private final LocalDateTime deliveredAt;
    private final BigDecimal shippingFee;
    private final String note;
    private final List<ShipmentEventResponse> events;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
