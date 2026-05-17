package com.locnguyen.ecommerce.domains.shipment.service;

import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ShipmentProviderUpdate(
        UUID shipmentId,
        ShipmentStatus status,
        String providerStatus,
        String trackingNumber,
        String trackingUrl,
        String location,
        String description,
        LocalDateTime eventTime
) {}
