package com.locnguyen.ecommerce.domains.carrier.provider;

import com.locnguyen.ecommerce.domains.order.entity.Order;
import lombok.Builder;

@Builder
public record ShippingRateRequest(
        Order order
) {}
