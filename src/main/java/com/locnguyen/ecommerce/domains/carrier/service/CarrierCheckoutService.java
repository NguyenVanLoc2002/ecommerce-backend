package com.locnguyen.ecommerce.domains.carrier.service;

import com.locnguyen.ecommerce.domains.carrier.dto.CheckoutCarrierOptionResponse;
import com.locnguyen.ecommerce.domains.carrier.dto.CheckoutCarrierQuoteResponse;
import com.locnguyen.ecommerce.domains.order.entity.Order;

import java.util.List;
import java.util.UUID;

public interface CarrierCheckoutService {

    List<CheckoutCarrierOptionResponse> getCheckoutOptions();

    CheckoutCarrierQuoteResponse quote(UUID carrierId, Order order);
}
