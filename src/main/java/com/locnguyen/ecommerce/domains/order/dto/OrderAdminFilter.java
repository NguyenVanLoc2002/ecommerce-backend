package com.locnguyen.ecommerce.domains.order.dto;

import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * Admin-facing query filter for listing all orders.
 * All fields are optional — omitting a field removes it from the WHERE clause.
 */
@Getter
@Setter
public class OrderAdminFilter {

    /** Filter by customer ID. */
    private Long customerId;

    /** Filter by order status. */
    private OrderStatus status;

    /** Filter by payment status. */
    private PaymentStatus paymentStatus;
}
