package com.locnguyen.ecommerce.domains.order.repository;

import java.util.UUID;

public interface OrderItemCountProjection {
    UUID getOrderId();
    Long getTotalQuantity();
}
