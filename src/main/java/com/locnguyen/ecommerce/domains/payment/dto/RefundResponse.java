package com.locnguyen.ecommerce.domains.payment.dto;

import com.locnguyen.ecommerce.domains.payment.enums.RefundStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record RefundResponse(
        UUID id,
        UUID paymentId,
        String refundCode,
        BigDecimal amount,
        String reason,
        RefundStatus status,
        String providerRefundId,
        LocalDateTime refundedAt,
        String requestedBy,
        String note,
        LocalDateTime createdAt
) {}
