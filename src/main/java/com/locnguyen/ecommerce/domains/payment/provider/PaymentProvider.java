package com.locnguyen.ecommerce.domains.payment.provider;

/**
 * Strategy interface for payment gateway integrations.
 * Implement one bean per gateway (MoMo, ZaloPay, VNPay, etc.).
 */
public interface PaymentProvider {

    /** Unique identifier for this provider (e.g., "MOMO", "VNPAY", "ZALOPAY"). */
    String getProviderName();

    /**
     * Verify the signature/HMAC of an incoming webhook payload.
     *
     * @param rawBody   raw request body bytes as received
     * @param signature signature header value from the gateway
     * @return true if the signature is valid
     */
    boolean verifySignature(String rawBody, String signature);

    /**
     * Determine whether the callback payload represents a successful payment.
     *
     * @param payload raw callback payload
     * @return true if the gateway reported payment success
     */
    boolean isSuccess(String payload);

    /**
     * Extract the provider's own transaction ID from the callback payload.
     *
     * @param payload raw callback payload
     * @return provider transaction ID, or null if not available
     */
    String extractProviderTxnId(String payload);

    /**
     * Extract the order reference (orderCode) from the callback payload.
     *
     * @param payload raw callback payload
     * @return order code string
     */
    String extractOrderCode(String payload);
}
