package com.locnguyen.ecommerce.infrastructure.payment.paypal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProvider;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProviderCaptureResult;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProviderCreateResult;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalAmount;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCapture;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCaptureOrderResponse;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCreateOrderRequest;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCreateOrderResponse;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalExperienceContext;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalPaymentSource;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalPaymentSourcePaypal;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalPurchaseUnit;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalWebhookEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PayPal payment provider using Orders API v2 and Webhooks API v1.
 *
 * <p>Active only when {@code app.payment.paypal.enabled=true}.
 *
 * <p>Flow:
 * <ol>
 *   <li>Initiate: {@link #createPayment} → creates a PayPal order, returns approval URL.</li>
 *   <li>Capture-on-return: customer returns from PayPal, frontend sends token to
 *       {@code POST /api/v1/payments/order/{orderId}/capture} → {@link #capturePayment}.</li>
 *   <li>Webhook reconciliation: PayPal sends {@code PAYMENT.CAPTURE.COMPLETED} to the
 *       PAYPAL-specific webhook endpoint → {@link #verifySignature} / {@link #isSuccess} /
 *       {@link #extractOrderCode} / {@link #extractProviderTxnId}.</li>
 * </ol>
 *
 * <p>Currency note: orders are stored in VND. This provider requires USD by default.
 * Enable {@code app.payment.paypal.test-conversion-enabled=true} for sandbox testing only.
 *
 * <p>Never logs {@code clientSecret} or the raw access token.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payment.paypal.enabled", havingValue = "true")
public class PaypalPaymentProvider implements PaymentProvider {

    static final String PROVIDER_NAME = "PAYPAL";

    private static final String BRAND_NAME = "Locen Studio";
    private static final String LOCALE = "en-US";
    private static final String INTENT_CAPTURE = "CAPTURE";
    private static final String EVENT_CAPTURE_COMPLETED = "PAYMENT.CAPTURE.COMPLETED";

    private final PaypalPaymentProperties properties;
    private final PaypalClient paypalClient;
    private final ObjectMapper objectMapper;

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    // ─── Payment creation ─────────────────────────────────────────────────────

    /**
     * Creates a PayPal order and returns the customer approval URL.
     *
     * <p>Sets {@code custom_id = orderCode} on the purchase unit so that
     * capture webhooks include the orderCode in {@code resource.custom_id}.
     */
    @Override
    public PaymentProviderCreateResult createPayment(Payment payment, Order order,
                                                     String returnUrl, String callbackUrl) {
        BigDecimal paypalAmount = resolvePaypalAmount(payment.getAmount(), order.getOrderCode());
        String effectiveReturnUrl = (returnUrl != null && !returnUrl.isBlank())
                ? returnUrl.trim()
                : properties.getReturnUrl();

        PaypalCreateOrderRequest createRequest = buildCreateOrderRequest(
                order.getOrderCode(), paypalAmount, effectiveReturnUrl);

        log.info("Calling PayPal create-order: orderCode={} amount={} currency={}",
                order.getOrderCode(), paypalAmount, properties.getCurrency());

        PaypalCreateOrderResponse response = paypalClient.createOrder(createRequest, order.getOrderCode());

        String approvalUrl = response.findApprovalUrl()
                .orElseThrow(() -> {
                    log.error("PayPal create-order response missing approval link: orderCode={} paypalOrderId={}",
                            order.getOrderCode(), response.getId());
                    return new AppException(ErrorCode.PAYMENT_FAILED,
                            "PayPal response did not contain an approval URL");
                });

        log.info("PayPal create-order succeeded: orderCode={} paypalOrderId={} status={}",
                order.getOrderCode(), response.getId(), response.getStatus());

        return PaymentProviderCreateResult.builder()
                .paymentUrl(approvalUrl)
                .providerOrderId(response.getId())
                .providerRequestId(response.getId())
                .message(response.getStatus())
                .build();
    }

    @Override
    public String createPaymentUrl(Payment payment, Order order, String returnUrl, String callbackUrl) {
        return createPayment(payment, order, returnUrl, callbackUrl).getPaymentUrl();
    }

    // ─── Capture ─────────────────────────────────────────────────────────────

    /**
     * Calls {@code POST /v2/checkout/orders/{paypalOrderId}/capture} and maps the result.
     *
     * <p>Returns success when the first capture has status {@code COMPLETED}.
     */
    @Override
    public Optional<PaymentProviderCaptureResult> capturePayment(Payment payment, String providerToken) {
        log.info("Capturing PayPal payment: paypalOrderId={} paymentCode={}",
                providerToken, payment.getPaymentCode());

        PaypalCaptureOrderResponse response = paypalClient.captureOrder(providerToken);
        PaypalCapture capture = response.firstCapture().orElse(null);

        if (capture == null) {
            log.error("PayPal capture response contained no capture objects: paypalOrderId={} status={}",
                    providerToken, response.getStatus());
            return Optional.of(PaymentProviderCaptureResult.builder()
                    .success(false)
                    .status(response.getStatus())
                    .message("No capture in PayPal response")
                    .build());
        }

        boolean success = "COMPLETED".equals(capture.getStatus());
        log.info("PayPal capture result: paypalOrderId={} captureId={} status={} success={}",
                providerToken, capture.getId(), capture.getStatus(), success);

        return Optional.of(PaymentProviderCaptureResult.builder()
                .success(success)
                .providerTxnId(capture.getId())
                .status(capture.getStatus())
                .message(response.getStatus())
                .build());
    }

    // ─── Webhook methods ─────────────────────────────────────────────────────

    /**
     * Verifies the PayPal webhook signature via the PayPal Webhooks API v1.
     *
     * <p>The {@code signature} parameter is expected to be a JSON string containing
     * the five PayPal-specific headers serialized as a map:
     * {@code paypal_auth_algo, paypal_cert_url, paypal_transmission_id,
     * paypal_transmission_sig, paypal_transmission_time}.
     *
     * <p>Returns {@code false} on any parse or API error — the webhook flow will then
     * reject the event with {@code PAYMENT_WEBHOOK_SIGNATURE_INVALID}.
     */
    @Override
    public boolean verifySignature(String rawBody, String signature) {
        if (signature == null || signature.isBlank()) {
            log.warn("PayPal webhook received with null/blank signature headers JSON");
            return false;
        }

        Map<String, String> headers;
        try {
            headers = objectMapper.readValue(signature, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse PayPal webhook headers from signature param: {}", e.getMessage());
            return false;
        }

        return paypalClient.verifyWebhookSignature(
                headers.getOrDefault("paypal_auth_algo", ""),
                headers.getOrDefault("paypal_cert_url", ""),
                headers.getOrDefault("paypal_transmission_id", ""),
                headers.getOrDefault("paypal_transmission_sig", ""),
                headers.getOrDefault("paypal_transmission_time", ""),
                rawBody
        );
    }

    /**
     * Returns {@code true} for {@code PAYMENT.CAPTURE.COMPLETED} events whose
     * resource status is {@code COMPLETED}.
     */
    @Override
    public boolean isSuccess(String payload) {
        try {
            PaypalWebhookEvent event = objectMapper.readValue(payload, PaypalWebhookEvent.class);
            return EVENT_CAPTURE_COMPLETED.equals(event.getEventType())
                    && event.getResource() != null
                    && "COMPLETED".equals(event.getResource().getStatus());
        } catch (Exception e) {
            log.warn("Failed to parse PayPal webhook payload in isSuccess: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extracts the capture ID from the webhook resource — used as {@code providerTxnId}.
     * Returns {@code null} for non-capture events (e.g., CHECKOUT.ORDER.APPROVED).
     */
    @Override
    public String extractProviderTxnId(String payload) {
        try {
            PaypalWebhookEvent event = objectMapper.readValue(payload, PaypalWebhookEvent.class);
            if (event.getResource() == null) return null;
            return event.getResource().getId();
        } catch (Exception e) {
            log.warn("Failed to extract providerTxnId from PayPal webhook payload: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the order code from {@code resource.custom_id}, which is set to the
     * orderCode during order creation. Returns {@code null} for events where
     * {@code custom_id} is absent (e.g., CHECKOUT.ORDER.APPROVED at the order level).
     */
    @Override
    public String extractOrderCode(String payload) {
        try {
            PaypalWebhookEvent event = objectMapper.readValue(payload, PaypalWebhookEvent.class);
            if (event.getResource() == null) return null;
            return event.getResource().getCustomId();
        } catch (Exception e) {
            log.warn("Failed to extract orderCode from PayPal webhook payload: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Returns {@code null} — PayPal reports amounts in USD while the stored payment
     * amount is in VND. Returning null skips the amount guard in the webhook processor,
     * which is correct since amount validation is done explicitly in the capture endpoint.
     */
    @Override
    public BigDecimal extractAmount(String payload) {
        return null;
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private PaypalCreateOrderRequest buildCreateOrderRequest(
            String orderCode, BigDecimal amount, String returnUrl) {
        return PaypalCreateOrderRequest.builder()
                .intent(INTENT_CAPTURE)
                .purchaseUnits(List.of(
                        PaypalPurchaseUnit.builder()
                                .referenceId(orderCode)
                                .customId(orderCode)
                                .amount(PaypalAmount.builder()
                                        .currencyCode(properties.getCurrency())
                                        .value(formatAmount(amount))
                                        .build())
                                .build()))
                .paymentSource(PaypalPaymentSource.builder()
                        .paypal(PaypalPaymentSourcePaypal.builder()
                                .experienceContext(PaypalExperienceContext.builder()
                                        .brandName(BRAND_NAME)
                                        .locale(LOCALE)
                                        .userAction("PAY_NOW")
                                        .paymentMethodPreference("IMMEDIATE_PAYMENT_REQUIRED")
                                        .shippingPreference("NO_SHIPPING")
                                        .returnUrl(returnUrl)
                                        .cancelUrl(properties.getCancelUrl())
                                        .build())
                                .build())
                        .build())
                .build();
    }

    /**
     * Converts the order amount (VND) to the configured PayPal currency (USD by default).
     *
     * <p>When {@code testConversionEnabled=false} and currency is USD, throws
     * {@link ErrorCode#PAYMENT_CURRENCY_UNSUPPORTED}. Enable test conversion only for sandbox.
     */
    private BigDecimal resolvePaypalAmount(BigDecimal orderAmount, String orderCode) {
        if (!"USD".equalsIgnoreCase(properties.getCurrency())) {
            return orderAmount.setScale(2, RoundingMode.HALF_UP);
        }

        if (!properties.isTestConversionEnabled()) {
            log.error("PayPal currency mismatch: orders are VND but PayPal is configured for USD. "
                    + "Set app.payment.paypal.test-conversion-enabled=true for sandbox testing. "
                    + "orderCode={}", orderCode);
            throw new AppException(ErrorCode.PAYMENT_CURRENCY_UNSUPPORTED,
                    "Order amount is in VND but PayPal is configured for USD. "
                    + "Enable app.payment.paypal.test-conversion-enabled=true for sandbox testing.");
        }

        BigDecimal rate = properties.getTestConversionRateVndToUsd();
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.PAYMENT_FAILED,
                    "Invalid testConversionRateVndToUsd — must be > 0");
        }
        BigDecimal usdAmount = orderAmount.divide(rate, 2, RoundingMode.HALF_UP);
        log.debug("Test VND→USD conversion: orderCode={} vnd={} rate={} usd={}",
                orderCode, orderAmount, rate, usdAmount);
        return usdAmount;
    }

    private static String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
