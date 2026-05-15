package com.locnguyen.ecommerce.domains.payment.provider;

import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.payment.entity.Payment;

import java.math.BigDecimal;
import java.util.Optional;

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

    /**
     * Extract the payment amount from the callback payload for server-side amount validation.
     *
     * <p>Providers that embed the amount in their IPN payload should override this method.
     * The returned value is compared to the stored {@link Payment#getAmount()} before any
     * mutation occurs — a mismatch causes the webhook to be rejected.
     *
     * @param payload raw callback payload
     * @return payment amount as received from the gateway, or {@code null} if not available
     */
    default BigDecimal extractAmount(String payload) {
        return null;
    }

    /**
     * Capture an authorized payment.
     *
     * <p>Used by provider flows where payment authorization and capture are separate steps
     * (e.g., PayPal Orders API: create-order → customer approves → capture).
     *
     * <p>The default implementation returns {@link Optional#empty()} — providers that
     * combine authorization and capture in a single step should not override this.
     *
     * @param payment      the payment record with {@code providerOrderId} already set
     * @param providerToken the provider-assigned order token returned at initiation
     * @return capture result, or {@link Optional#empty()} if capture is not supported
     */
    default Optional<PaymentProviderCaptureResult> capturePayment(Payment payment, String providerToken) {
        return Optional.empty();
    }

    /**
     * Generate the URL the customer should be redirected to for completing payment.
     *
     * <p>Prefer {@link #createPayment} when you need additional provider references
     * (deeplink, qrCodeUrl, providerOrderId). This method is kept for backward
     * compatibility with providers that only return a single URL.
     *
     * @param payment     the payment record (contains paymentCode, amount, expiredAt)
     * @param order       the order being paid (contains orderCode, totalAmount)
     * @param returnUrl   URL to redirect the customer back to after payment
     * @param callbackUrl URL the gateway calls asynchronously after payment
     * @return payment URL string, or {@code null} if URL generation is not supported
     */
    String createPaymentUrl(Payment payment, Order order, String returnUrl, String callbackUrl);

    /**
     * Create a payment request with the gateway and return the full result.
     *
     * <p>The default implementation wraps {@link #createPaymentUrl} for backward
     * compatibility. Providers that return deeplinks, QR data, or provider-scoped
     * identifiers (e.g. MoMo) should override this method.
     *
     * @param payment     the payment record
     * @param order       the order being paid
     * @param returnUrl   URL to redirect the customer back to after payment
     * @param callbackUrl URL the gateway calls asynchronously after payment
     * @return result containing paymentUrl and any provider-specific references
     */
    default PaymentProviderCreateResult createPayment(Payment payment, Order order,
                                                      String returnUrl, String callbackUrl) {
        String url = createPaymentUrl(payment, order, returnUrl, callbackUrl);
        return PaymentProviderCreateResult.builder().paymentUrl(url).build();
    }
}
