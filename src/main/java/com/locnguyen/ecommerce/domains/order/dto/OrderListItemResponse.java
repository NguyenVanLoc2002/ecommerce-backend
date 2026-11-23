package com.locnguyen.ecommerce.domains.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.List;
import java.util.UUID;
/**
 * Lightweight order summary with a bounded item preview.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Order list item response — summary for list views")
public class OrderListItemResponse {

    private final UUID id;
    private final String orderCode;
    private final OrderStatus status;
    private final PaymentMethod paymentMethod;
    private final PaymentStatus paymentStatus;
    private final int totalItems;
    private final List<OrderItemResponse> itemsPreview;
    private final int remainingItemLines;
    private final BigDecimal totalAmount;
    private final LocalDateTime createdAt;
}
