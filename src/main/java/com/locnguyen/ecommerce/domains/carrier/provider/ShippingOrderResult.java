package com.locnguyen.ecommerce.domains.carrier.provider;

import lombok.Builder;

@Builder
public record ShippingOrderResult(
        String carrierShipmentId,
        String trackingNumber,
        String trackingUrl,
        String labelUrl,
        String rawStatus
) {}
