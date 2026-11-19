package com.locnguyen.ecommerce.domains.shipment.dto;

import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.shipment.enums.CancellationOperationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class CancellationOperationResponse {
    private final UUID id;
    private final UUID orderId;
    private final String orderCode;
    private final UUID shipmentId;
    private final String idempotencyKey;
    private final CancellationOperationStatus status;
    private final OrderStatus orderStatus;
    private final String lastErrorCode;
    private final LocalDateTime requestedAt;
    private final LocalDateTime completedAt;
}
