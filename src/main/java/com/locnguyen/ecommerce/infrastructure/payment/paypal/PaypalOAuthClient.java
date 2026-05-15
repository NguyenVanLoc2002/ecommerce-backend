package com.locnguyen.ecommerce.infrastructure.payment.paypal;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalAccessTokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Obtains and caches a PayPal OAuth 2.0 access token.
 *
 * <p>Uses the {@code client_credentials} grant with HTTP Basic auth.
 * The token is cached in memory until 60 seconds before its expiry to
 * avoid race conditions at the boundary.
 *
 * <p>Never logs {@code clientSecret} or the raw {@code access_token}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payment.paypal.enabled", havingValue = "true")
public class PaypalOAuthClient {

    private static final int EXPIRY_BUFFER_SECONDS = 60;

    private final PaypalPaymentProperties properties;
    private final RestClient restClient;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.MIN;

    public PaypalOAuthClient(
            PaypalPaymentProperties properties,
            @Qualifier("paypalRestClient") RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    /**
     * Returns a valid access token, refreshing from PayPal if the cached one is
     * expired or not yet obtained.
     *
     * @throws AppException with {@link ErrorCode#PAYMENT_FAILED} if the token endpoint fails
     */
    public String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }
        return refreshToken();
    }

    private synchronized String refreshToken() {
        // Double-checked locking — another thread may have refreshed while we waited
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }

        log.info("Refreshing PayPal access token: environment={}", properties.getEnvironment());

        String credentials = Base64.getEncoder().encodeToString(
                (properties.getClientId() + ":" + properties.getClientSecret())
                        .getBytes(StandardCharsets.UTF_8));

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");

        PaypalAccessTokenResponse tokenResponse;
        try {
            tokenResponse = restClient.post()
                    .uri(properties.getBaseUrl() + "/v1/oauth2/token")
                    .header("Authorization", "Basic " + credentials)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(PaypalAccessTokenResponse.class);
        } catch (RestClientException e) {
            log.error("PayPal token endpoint call failed: error={}", e.getMessage());
            throw new AppException(ErrorCode.PAYMENT_FAILED,
                    "Failed to obtain PayPal access token: " + e.getMessage());
        }

        if (tokenResponse == null || tokenResponse.getAccessToken() == null
                || tokenResponse.getAccessToken().isBlank()) {
            log.error("PayPal token endpoint returned empty or missing access_token");
            throw new AppException(ErrorCode.PAYMENT_FAILED,
                    "PayPal returned an empty access token");
        }

        cachedToken = tokenResponse.getAccessToken();
        tokenExpiresAt = Instant.now()
                .plusSeconds(tokenResponse.getExpiresIn())
                .minusSeconds(EXPIRY_BUFFER_SECONDS);

        log.info("PayPal access token refreshed: expiresIn={}s", tokenResponse.getExpiresIn());
        // Never log the token value itself
        return cachedToken;
    }
}
