package com.locnguyen.ecommerce.domains.admin.dto;

import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Enriched order list item for the admin dashboard.
 * Includes customer identity fields that are not exposed on the customer-facing list.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminOrderListItemResponse {

    private Long id;
    private String orderCode;

    private Long customerId;
    private String customerName;
    private String customerEmail;

    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;

    private int totalItems;
    private BigDecimal totalAmount;

    private LocalDateTime createdAt;
}
