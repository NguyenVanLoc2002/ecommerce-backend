package com.locnguyen.ecommerce.domains.payment.dto;

import com.locnguyen.ecommerce.domains.payment.enums.WebhookLogStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record WebhookLogResponse(
        UUID id,
        UUID paymentId,
        String provider,
        String orderCode,
        String providerTxnId,
        Boolean signatureValid,
        WebhookLogStatus status,
        LocalDateTime processedAt,
        String errorMessage,
        LocalDateTime createdAt
) {}
