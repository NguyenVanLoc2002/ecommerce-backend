package com.locnguyen.ecommerce.domains.payment.mapper;

import com.locnguyen.ecommerce.domains.payment.dto.WebhookLogResponse;
import com.locnguyen.ecommerce.domains.payment.entity.PaymentWebhookLog;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PaymentWebhookLogMapper {

    default WebhookLogResponse toResponse(PaymentWebhookLog log) {
        if (log == null) return null;

        return new WebhookLogResponse(
                log.getId(),
                log.getPayment() != null ? log.getPayment().getId() : null,
                log.getProvider(),
                log.getOrderCode(),
                log.getProviderTxnId(),
                log.getSignatureValid(),
                log.getStatus(),
                log.getProcessedAt(),
                log.getErrorMessage(),
                log.getCreatedAt()
        );
    }

    default List<WebhookLogResponse> toResponses(List<PaymentWebhookLog> logs) {
        if (logs == null) return List.of();
        return logs.stream().map(this::toResponse).toList();
    }
}
