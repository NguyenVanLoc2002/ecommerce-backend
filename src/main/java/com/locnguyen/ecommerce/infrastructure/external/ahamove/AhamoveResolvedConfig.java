package com.locnguyen.ecommerce.infrastructure.external.ahamove;

import java.math.BigDecimal;
import java.util.List;

public record AhamoveResolvedConfig(
        String apiBaseUrl,
        String apiKey,
        String phone,
        String brandName,
        String webhookToken,
        String pickupAddress,
        String pickupShortAddress,
        String pickupName,
        String pickupPhone,
        BigDecimal pickupLat,
        BigDecimal pickupLng,
        String groupServiceId,
        String paymentMethod,
        List<AhamoveConfigData.RequestOption> groupRequests
) {}
