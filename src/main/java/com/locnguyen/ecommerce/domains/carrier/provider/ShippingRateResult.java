package com.locnguyen.ecommerce.domains.carrier.provider;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record ShippingRateResult(
        BigDecimal fee,
        String currency,
        String serviceName
) {}
