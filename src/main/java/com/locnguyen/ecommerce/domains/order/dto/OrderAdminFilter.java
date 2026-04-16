package com.locnguyen.ecommerce.domains.order.dto;

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

    /** Filter by order status (OrderStatus enum name). */
    private String status;

    /** Filter by payment status (PaymentStatus enum name). */
    private String paymentStatus;
}
