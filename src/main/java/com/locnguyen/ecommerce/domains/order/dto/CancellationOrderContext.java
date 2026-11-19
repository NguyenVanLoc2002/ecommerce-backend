package com.locnguyen.ecommerce.domains.order.dto;

import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;

import java.util.UUID;

public record CancellationOrderContext(UUID id, String orderCode, OrderStatus status) {}
