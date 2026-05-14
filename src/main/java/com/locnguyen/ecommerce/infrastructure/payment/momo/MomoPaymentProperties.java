package com.locnguyen.ecommerce.infrastructure.payment.momo;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly-typed configuration properties for the MoMo payment provider.
 *
 * <p>All sensitive values (partnerCode, accessKey, secretKey) must be supplied
 * via environment variables — never commit real credentials.
 *
 * <p>When {@code enabled=false} (default), no credential validation is performed
 * and the MoMo provider bean is not registered.
 */
@ConfigurationProperties(prefix = "app.payment.momo")
@Validated
@Getter
@Setter
public class MomoPaymentProperties {

    private boolean enabled = false;

    /** TEST or PROD environment label (informational; use the correct createUrl for each). */
    private String environment = "TEST";

    /** MoMo merchant partner code — set via {@code APP_PAYMENT_MOMO_PARTNER_CODE}. */
    private String partnerCode = "";

    /** MoMo access key (public identifier) — set via {@code APP_PAYMENT_MOMO_ACCESS_KEY}. */
    private String accessKey = "";

    /** MoMo secret key for HMAC signing — set via {@code APP_PAYMENT_MOMO_SECRET_KEY}. Never log this. */
    private String secretKey = "";

    /** MoMo create-payment API endpoint. Defaults to the test sandbox URL. */
    private String createUrl = "https://test-payment.momo.vn/v2/gateway/api/create";

    /** URL the customer is redirected to after completing or cancelling payment. */
    private String redirectUrl = "";

    /** Server-to-server IPN callback URL — must be publicly reachable (use a tunnel for local dev). */
    private String ipnUrl = "";

    /** Payment type — use {@code captureWallet} for MoMo wallet one-time payment. */
    private String requestType = "captureWallet";

    /** Response language: {@code vi} (Vietnamese) or {@code en} (English). */
    private String lang = "vi";

    @Min(value = 30_000, message = "app.payment.momo.connect-timeout-ms must be >= 30000 ms")
    private int connectTimeoutMs = 30_000;

    @Min(value = 30_000, message = "app.payment.momo.read-timeout-ms must be >= 30000 ms")
    private int readTimeoutMs = 30_000;

    /**
     * Validates that all required credentials are present when the provider is enabled.
     * Returns {@code true} unconditionally when {@code enabled=false}.
     */
    @AssertTrue(message = "partnerCode, accessKey, secretKey, createUrl, redirectUrl, and ipnUrl must not be blank when app.payment.momo.enabled=true")
    public boolean isCredentialsValid() {
        if (!enabled) return true;
        return !isBlank(partnerCode)
                && !isBlank(accessKey)
                && !isBlank(secretKey)
                && !isBlank(createUrl)
                && !isBlank(redirectUrl)
                && !isBlank(ipnUrl);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
