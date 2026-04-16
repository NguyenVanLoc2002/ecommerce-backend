package com.locnguyen.ecommerce.domains.admin.dto;

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

    // ─── Customer identity ───────────────────────────────────────────────────
    private Long customerId;
    private String customerName;
    private String customerEmail;

    // ─── Order state ─────────────────────────────────────────────────────────
    private String status;
    private String paymentMethod;
    private String paymentStatus;

    // ─── Amounts ─────────────────────────────────────────────────────────────
    private int totalItems;
    private BigDecimal totalAmount;

    private LocalDateTime createdAt;
}
