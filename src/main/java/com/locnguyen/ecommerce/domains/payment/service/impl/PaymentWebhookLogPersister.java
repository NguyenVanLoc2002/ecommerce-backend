package com.locnguyen.ecommerce.domains.payment.service.impl;

import com.locnguyen.ecommerce.domains.payment.entity.PaymentWebhookLog;
import com.locnguyen.ecommerce.domains.payment.enums.WebhookLogStatus;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentWebhookLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Separate Spring-managed component for webhook log persistence.
 *
 * <p>Required so that {@link #createInitialLog} runs in its own committed transaction
 * ({@code REQUIRES_NEW}) even when called from within a larger transaction in
 * {@link PaymentWebhookServiceImpl}. A self-call on the same bean would bypass the
 * Spring proxy and lose the propagation semantics.
 */
@Component
@RequiredArgsConstructor
public class PaymentWebhookLogPersister {

    private final PaymentWebhookLogRepository webhookLogRepository;

    /**
     * Persist an initial RECEIVED webhook log in a brand-new transaction that is
     * committed immediately — guarantees an audit trace even when subsequent
     * processing fails or the outer transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentWebhookLog createInitialLog(String provider, String rawBody, String signature) {
        PaymentWebhookLog webhookLog = new PaymentWebhookLog();
        webhookLog.setProvider(provider != null ? provider : "UNKNOWN");
        webhookLog.setPayload(rawBody);
        webhookLog.setSignature(signature);
        webhookLog.setStatus(WebhookLogStatus.RECEIVED);
        return webhookLogRepository.save(webhookLog);
    }
}
