package com.locnguyen.ecommerce.domains.carrier.provider;

import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ShipmentTrackingResult(
        String trackingNumber,
        String trackingUrl,
        ShipmentStatus status,
        String rawStatus,
        String description,
        String location,
        LocalDateTime eventTime
) {}
