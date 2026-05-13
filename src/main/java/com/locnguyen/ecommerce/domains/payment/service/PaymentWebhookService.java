package com.locnguyen.ecommerce.domains.payment.service;

import com.locnguyen.ecommerce.domains.payment.dto.PaymentResponse;
import com.locnguyen.ecommerce.domains.payment.dto.WebhookLogResponse;

import java.util.List;
import java.util.UUID;

public interface PaymentWebhookService {

    /**
     * Receive and log a raw webhook from a gateway, then process it.
     * Handles: logging, signature verification, idempotency, state mutation.
     *
     * @return the updated PaymentResponse, or null if the provider is not registered
     */
    PaymentResponse receiveWebhook(String provider, String rawBody, String signature);

    List<WebhookLogResponse> getLogsForPayment(UUID paymentId);

    List<WebhookLogResponse> getLogsByProviderTxnId(String providerTxnId);
}
