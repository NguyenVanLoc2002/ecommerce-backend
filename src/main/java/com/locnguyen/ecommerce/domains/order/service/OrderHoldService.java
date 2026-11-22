package com.locnguyen.ecommerce.domains.order.service;

import com.locnguyen.ecommerce.domains.order.entity.Order;
import java.time.LocalDateTime;

public interface OrderHoldService {
    void expireOrder(String orderCode);

    boolean acceptOnlineSuccess(Order order);

    void retainForFulfillment(Order order);

    void requireFulfillmentHold(Order order);

    LocalDateTime getCheckoutExpiry(Order order);
}
