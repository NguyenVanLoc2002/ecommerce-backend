package com.locnguyen.ecommerce.infrastructure.payment.paypal;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Strongly-typed configuration properties for the PayPal payment provider.
 *
 * <p>All sensitive values (clientId, clientSecret) must be supplied via environment
 * variables — never commit real credentials.
 *
 * <p>When {@code enabled=false} (default), no credential validation is performed
 * and the PayPal provider bean is not registered.
 *
 * <p>Currency note: orders are stored in VND internally. PayPal defaults to USD.
 * Enable {@code testConversionEnabled} to apply a fixed VND→USD rate for sandbox
 * testing. Never use this in production — implement a proper currency conversion
 * service before going live with PayPal.
 *
 * <p>{@code webhookId} is reserved for Session 2 (webhook signature verification).
 * It is not validated in Session 1.
 */
@ConfigurationProperties(prefix = "app.payment.paypal")
@Validated
@Getter
@Setter
public class PaypalPaymentProperties {

    private boolean enabled = false;

    /** SANDBOX or LIVE — informational label; use the correct baseUrl for each. */
    private String environment = "SANDBOX";

    /** PayPal OAuth 2.0 client ID — set via {@code APP_PAYMENT_PAYPAL_CLIENT_ID}. */
    private String clientId = "";

    /** PayPal OAuth 2.0 client secret — set via {@code APP_PAYMENT_PAYPAL_CLIENT_SECRET}. Never log this. */
    private String clientSecret = "";

    /** PayPal API base URL. Defaults to sandbox. */
    private String baseUrl = "https://api-m.sandbox.paypal.com";

    /** URL PayPal redirects the customer to after successful approval. */
    private String returnUrl = "http://localhost:5173/payment/paypal/return";

    /** URL PayPal redirects the customer to when they cancel the checkout. */
    private String cancelUrl = "http://localhost:5173/payment/paypal/cancel";

    /**
     * PayPal webhook ID — required for Session 2 HMAC signature verification.
     * Not validated in Session 1.
     */
    private String webhookId = "";

    /** ISO 4217 currency code for PayPal orders. Defaults to USD. */
    private String currency = "USD";

    /**
     * Enable test-only VND → configured-currency conversion.
     *
     * <p>When {@code true}, the provider divides the VND order amount by
     * {@link #testConversionRateVndToUsd} to produce a USD amount before calling
     * the PayPal API.
     *
     * <p><strong>Never enable in production.</strong> Use a proper currency conversion
     * service instead. This flag exists solely for sandbox testing while the project
     * currency model is VND-only.
     */
    private boolean testConversionEnabled = false;

    /**
     * VND amount per 1 USD for test-only conversion.
     * Only used when {@link #testConversionEnabled} is {@code true}.
     */
    private BigDecimal testConversionRateVndToUsd = new BigDecimal("25000");

    @Min(value = 30_000, message = "app.payment.paypal.connect-timeout-ms must be >= 30000 ms")
    private int connectTimeoutMs = 30_000;

    @Min(value = 30_000, message = "app.payment.paypal.read-timeout-ms must be >= 30000 ms")
    private int readTimeoutMs = 30_000;

    /**
     * Trims whitespace from all string properties after binding.
     * Prevents subtle mismatches caused by trailing \r\n in Windows .env files.
     */
    @PostConstruct
    public void trimAll() {
        if (clientId != null) clientId = clientId.trim();
        if (clientSecret != null) clientSecret = clientSecret.trim();
        if (baseUrl != null) baseUrl = baseUrl.trim();
        if (returnUrl != null) returnUrl = returnUrl.trim();
        if (cancelUrl != null) cancelUrl = cancelUrl.trim();
        if (webhookId != null) webhookId = webhookId.trim();
        if (currency != null) currency = currency.trim();
        if (environment != null) environment = environment.trim();
    }

    /**
     * Validates that all required credentials are present when the provider is enabled.
     * Returns {@code true} unconditionally when {@code enabled=false}.
     */
    @AssertTrue(message = "clientId, clientSecret, baseUrl, returnUrl, and cancelUrl must not be blank when app.payment.paypal.enabled=true")
    public boolean isCredentialsValid() {
        if (!enabled) return true;
        return !isBlank(clientId)
                && !isBlank(clientSecret)
                && !isBlank(baseUrl)
                && !isBlank(returnUrl)
                && !isBlank(cancelUrl);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
