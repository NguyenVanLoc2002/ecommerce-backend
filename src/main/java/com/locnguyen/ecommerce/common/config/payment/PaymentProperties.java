package com.locnguyen.ecommerce.common.config.payment;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Centralized configuration for payment integration.
 *
 * <p>Keeps generic payment flow settings and provider-specific credentials/options
 * under a single strongly-typed config tree so payment integration does not depend
 * on scattered environment-variable lookups in business code.
 */
@ConfigurationProperties(prefix = "app.payment")
@Validated
@Getter
@Setter
public class PaymentProperties {

    /**
     * Base URL the application is reachable at, used to build payment callback URLs.
     * Must NOT end with a slash.
     */
    private String baseCallbackUrl = "http://localhost:8080/api/v1/payments";

    /**
     * Default customer return URL after payment if the request does not override it.
     */
    private String defaultReturnUrl = "http://localhost:3000/payment/result";

    /**
     * Base64-encoded AES key used to encrypt payment provider secrets stored in DB.
     */
    private String configEncryptionKey;

    @Valid
    private Mock mock = new Mock();

    @Valid
    private Momo momo = new Momo();

    @Valid
    private Paypal paypal = new Paypal();

    @PostConstruct
    public void trimAll() {
        baseCallbackUrl = trim(baseCallbackUrl);
        defaultReturnUrl = trim(defaultReturnUrl);
        if (momo != null) {
            momo.trimAll();
        }
        if (paypal != null) {
            paypal.trimAll();
        }
    }

    @Getter
    @Setter
    public static class Mock {
        private boolean enabled = false;
    }

    @Getter
    @Setter
    public static class Momo {

        private boolean enabled = false;

        /** TEST or PROD environment label (informational; use the correct createUrl for each). */
        private String environment = "TEST";

        /** MoMo merchant partner code. */
        private String partnerCode = "";

        /** MoMo access key (public identifier). */
        private String accessKey = "";

        /** MoMo secret key for HMAC signing. Never log this. */
        private String secretKey = "";

        /** MoMo create-payment API endpoint. Defaults to the test sandbox URL. */
        private String createUrl = "https://test-payment.momo.vn/v2/gateway/api/create";

        /** URL the customer is redirected to after completing or cancelling payment. */
        private String redirectUrl = "";

        /** Server-to-server IPN callback URL; must be publicly reachable. */
        private String ipnUrl = "";

        /** Payment type, typically {@code captureWallet}. */
        private String requestType = "captureWallet";

        /** Response language, usually {@code vi} or {@code en}. */
        private String lang = "vi";

        @Min(value = 30_000, message = "app.payment.momo.connect-timeout-ms must be >= 30000 ms")
        private int connectTimeoutMs = 30_000;

        @Min(value = 30_000, message = "app.payment.momo.read-timeout-ms must be >= 30000 ms")
        private int readTimeoutMs = 30_000;

        void trimAll() {
            environment = trim(environment);
            partnerCode = trim(partnerCode);
            accessKey = trim(accessKey);
            secretKey = trim(secretKey);
            createUrl = trim(createUrl);
            redirectUrl = trim(redirectUrl);
            ipnUrl = trim(ipnUrl);
            requestType = trim(requestType);
            lang = trim(lang);
        }

        @AssertTrue(message = "partnerCode, accessKey, secretKey, createUrl, redirectUrl, and ipnUrl must not be blank when app.payment.momo.enabled=true")
        public boolean isCredentialsValid() {
            if (!enabled) {
                return true;
            }
            return !isBlank(partnerCode)
                    && !isBlank(accessKey)
                    && !isBlank(secretKey)
                    && !isBlank(createUrl)
                    && !isBlank(redirectUrl)
                    && !isBlank(ipnUrl);
        }
    }

    @Getter
    @Setter
    public static class Paypal {

        private boolean enabled = false;

        /** SANDBOX or LIVE; informational label, use the correct baseUrl for each. */
        private String environment = "SANDBOX";

        /** PayPal OAuth 2.0 client ID. */
        private String clientId = "";

        /** PayPal OAuth 2.0 client secret. Never log this. */
        private String clientSecret = "";

        /** PayPal API base URL. Defaults to sandbox. */
        private String baseUrl = "https://api-m.sandbox.paypal.com";

        /** URL PayPal redirects the customer to after successful approval. */
        private String returnUrl = "http://localhost:5173/payment/paypal/return";

        /** URL PayPal redirects the customer to after cancellation. */
        private String cancelUrl = "http://localhost:5173/payment/paypal/cancel";

        /** PayPal webhook ID used for signature verification. */
        private String webhookId = "";

        /** ISO 4217 currency code for PayPal orders. Defaults to USD. */
        private String currency = "USD";

        /** PayPal checkout experience brand name shown to customers. */
        private String brandName = "Locen Studio";

        /** Checkout locale sent to PayPal. */
        private String locale = "en-US";

        /** PayPal checkout user action. */
        private String userAction = "PAY_NOW";

        /** PayPal checkout payment method preference. */
        private String paymentMethodPreference = "IMMEDIATE_PAYMENT_REQUIRED";

        /** PayPal checkout shipping preference. */
        private String shippingPreference = "NO_SHIPPING";

        /** VND per one USD, controlled by the platform and snapshotted at initiation. */
        private BigDecimal conversionRateVndToUsd;

        @Min(value = 30_000, message = "app.payment.paypal.connect-timeout-ms must be >= 30000 ms")
        private int connectTimeoutMs = 30_000;

        @Min(value = 30_000, message = "app.payment.paypal.read-timeout-ms must be >= 30000 ms")
        private int readTimeoutMs = 30_000;

        void trimAll() {
            environment = trim(environment);
            clientId = trim(clientId);
            clientSecret = trim(clientSecret);
            baseUrl = trim(baseUrl);
            returnUrl = trim(returnUrl);
            cancelUrl = trim(cancelUrl);
            webhookId = trim(webhookId);
            currency = trim(currency);
            brandName = trim(brandName);
            locale = trim(locale);
            userAction = trim(userAction);
            paymentMethodPreference = trim(paymentMethodPreference);
            shippingPreference = trim(shippingPreference);
        }

        @AssertTrue(message = "clientId, clientSecret, baseUrl, returnUrl, and cancelUrl must not be blank when app.payment.paypal.enabled=true")
        public boolean isCredentialsValid() {
            if (!enabled) {
                return true;
            }
            return !isBlank(clientId)
                    && !isBlank(clientSecret)
                    && !isBlank(baseUrl)
                    && !isBlank(returnUrl)
                    && !isBlank(cancelUrl);
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
