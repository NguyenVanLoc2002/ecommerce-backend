package com.locnguyen.ecommerce.infrastructure.payment.mock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock payment provider for local/dev/test environments ONLY.
 *
 * <p>Enabled when {@code app.payment.mock.enabled=true}. This bean is never
 * registered in production because the property is absent from
 * {@code application-prod.properties}.
 *
 * <p>Signature verification always passes. Payload parsing expects a simple JSON
 * object with {@code orderCode}, {@code providerTxnId}, and {@code status} fields.
 *
 * <p><strong>NEVER enable this in production.</strong>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payment.mock.enabled", havingValue = "true")
public class MockPaymentProvider implements PaymentProvider {

    static final String PROVIDER_NAME = "MOCK";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    /**
     * Always returns {@code true} — no real signature to verify in the mock provider.
     *
     * <strong>This is intentionally insecure and must never run in production.</strong>
     */
    @Override
    public boolean verifySignature(String rawBody, String signature) {
        return true;
    }

    /** Returns {@code true} when the payload's {@code status} field equals {@code "SUCCESS"} (case-insensitive). */
    @Override
    public boolean isSuccess(String payload) {
        MockPayload parsed = parsePayload(payload);
        return parsed != null && "SUCCESS".equalsIgnoreCase(parsed.status());
    }

    /** Returns the {@code providerTxnId} field value, or {@code null} if missing or unparseable. */
    @Override
    public String extractProviderTxnId(String payload) {
        MockPayload parsed = parsePayload(payload);
        return parsed != null ? parsed.providerTxnId() : null;
    }

    /** Returns the {@code orderCode} field value, or {@code null} if missing or unparseable. */
    @Override
    public String extractOrderCode(String payload) {
        MockPayload parsed = parsePayload(payload);
        return parsed != null ? parsed.orderCode() : null;
    }

    /**
     * Returns a self-contained mock checkout URL. The URL points to the built-in
     * completion endpoint so a developer can simulate payment success/failure in dev
     * by simply opening the link.
     *
     * <p>Format: {@code {callbackUrl}/mock/complete?orderCode=...&providerTxnId=MOCK-{uuid}&status=SUCCESS}
     */
    @Override
    public String createPaymentUrl(Payment payment, Order order, String returnUrl, String callbackUrl) {
        String baseUrl = callbackUrl != null ? callbackUrl : returnUrl;
        return baseUrl + "/mock/complete"
                + "?orderCode=" + order.getOrderCode()
                + "&providerTxnId=MOCK-" + payment.getPaymentCode()
                + "&status=SUCCESS";
    }

    private MockPayload parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(payload, MockPayload.class);
        } catch (Exception e) {
            log.debug("MockPaymentProvider: failed to parse payload — {}", e.getMessage());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MockPayload(String orderCode, String providerTxnId, String status) {}
}
