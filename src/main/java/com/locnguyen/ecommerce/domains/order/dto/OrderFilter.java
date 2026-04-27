package com.locnguyen.ecommerce.domains.order.dto;

import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Order filter parameters")
public class OrderFilter {

    @Schema(description = "Filter by status (PENDING, AWAITING_PAYMENT, CONFIRMED, etc.)")
    private OrderStatus status;
}
