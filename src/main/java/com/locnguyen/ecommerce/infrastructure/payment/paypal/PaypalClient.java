package com.locnguyen.ecommerce.infrastructure.payment.paypal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCaptureOrderResponse;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCreateOrderRequest;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCreateOrderResponse;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalWebhookVerifyRequest;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalWebhookVerifyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Low-level client for the PayPal Orders API v2 and Webhooks API v1.
 *
 * <p>Obtains a Bearer token from {@link PaypalOAuthClient} before each call.
 * All PayPal-specific JSON shapes are confined to the {@code dto} package.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payment.paypal.enabled", havingValue = "true")
public class PaypalClient {

    private final PaypalPaymentProperties properties;
    private final PaypalOAuthClient oAuthClient;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PaypalClient(
            PaypalPaymentProperties properties,
            PaypalOAuthClient oAuthClient,
            @Qualifier("paypalRestClient") RestClient restClient,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.oAuthClient = oAuthClient;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Calls {@code POST /v2/checkout/orders} and returns the full response.
     *
     * @param request   the order request body
     * @param orderCode internal order code used for logging context
     * @throws AppException with {@link ErrorCode#PAYMENT_FAILED} on any API error
     */
    public PaypalCreateOrderResponse createOrder(PaypalCreateOrderRequest request, String orderCode) {
        String token = oAuthClient.getAccessToken();

        log.info("Calling PayPal create-order API: orderCode={}", orderCode);

        try {
            PaypalCreateOrderResponse response = restClient.post()
                    .uri(properties.getBaseUrl() + "/v2/checkout/orders")
                    .header("Authorization", "Bearer " + token)
                    .header("Prefer", "return=representation")
                    .header("Accept", "application/json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(PaypalCreateOrderResponse.class);

            if (response == null) {
                throw new AppException(ErrorCode.PAYMENT_FAILED,
                        "PayPal create-order API returned empty response");
            }
            return response;
        } catch (AppException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("PayPal create-order API call failed: orderCode={} error={}", orderCode, e.getMessage());
            throw new AppException(ErrorCode.PAYMENT_FAILED,
                    "PayPal API call failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error calling PayPal create-order API: orderCode={} error={}",
                    orderCode, e.getMessage(), e);
            throw new AppException(ErrorCode.PAYMENT_FAILED,
                    "Unexpected error calling PayPal API");
        }
    }

    /**
     * Calls {@code POST /v2/checkout/orders/{paypalOrderId}/capture}.
     *
     * @param paypalOrderId the PayPal order ID returned during initiation
     * @throws AppException with {@link ErrorCode#PAYMENT_FAILED} on any API error
     */
    public PaypalCaptureOrderResponse captureOrder(String paypalOrderId) {
        String token = oAuthClient.getAccessToken();
        String captureUrl = properties.getBaseUrl() + "/v2/checkout/orders/" + paypalOrderId + "/capture";

        log.info("Calling PayPal capture-order API: paypalOrderId={} url={}", paypalOrderId, captureUrl);

        try {
            PaypalCaptureOrderResponse response = restClient.post()
                    .uri(captureUrl)
                    .header("Authorization", "Bearer " + token)
                    .header("Prefer", "return=representation")
                    .header("Accept", "application/json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve()
                    .body(PaypalCaptureOrderResponse.class);

            if (response == null) {
                throw new AppException(ErrorCode.PAYMENT_FAILED,
                        "PayPal capture-order API returned empty response");
            }
            return response;
        } catch (AppException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            log.error("PayPal capture-order API HTTP error: paypalOrderId={} status={} body={}",
                    paypalOrderId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new AppException(ErrorCode.PAYMENT_FAILED,
                    "PayPal capture failed [" + e.getStatusCode() + "]: " + e.getResponseBodyAsString());
        } catch (RestClientException e) {
            log.error("PayPal capture-order API call failed: paypalOrderId={} error={}", paypalOrderId, e.getMessage());
            throw new AppException(ErrorCode.PAYMENT_FAILED,
                    "PayPal capture API call failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error calling PayPal capture-order API: paypalOrderId={} error={}",
                    paypalOrderId, e.getMessage(), e);
            throw new AppException(ErrorCode.PAYMENT_FAILED,
                    "Unexpected error calling PayPal capture API");
        }
    }

    /**
     * Calls {@code POST /v1/notifications/verify-webhook-signature} to verify a PayPal webhook.
     *
     * <p>Returns {@code false} on any API error rather than throwing, so the webhook is
     * rejected safely without propagating infrastructure failures.
     *
     * @param authAlgo         value of {@code PAYPAL-AUTH-ALGO} header
     * @param certUrl          value of {@code PAYPAL-CERT-URL} header
     * @param transmissionId   value of {@code PAYPAL-TRANSMISSION-ID} header
     * @param transmissionSig  value of {@code PAYPAL-TRANSMISSION-SIG} header
     * @param transmissionTime value of {@code PAYPAL-TRANSMISSION-TIME} header
     * @param rawBody          the raw webhook body string
     * @return true if PayPal confirms the signature is valid
     */
    public boolean verifyWebhookSignature(
            String authAlgo, String certUrl, String transmissionId,
            String transmissionSig, String transmissionTime, String rawBody) {

        if (properties.getWebhookId() == null || properties.getWebhookId().isBlank()) {
            log.warn("PayPal webhook verification skipped — webhook-id not configured");
            return false;
        }

        try {
            String token = oAuthClient.getAccessToken();

            // Parse rawBody as a plain Object so Jackson embeds it as-is into the verify request
            Object webhookEvent;
            try {
                webhookEvent = objectMapper.readValue(rawBody, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse PayPal webhook body for signature verification: {}", e.getMessage());
                return false;
            }

            PaypalWebhookVerifyRequest verifyRequest = PaypalWebhookVerifyRequest.builder()
                    .authAlgo(authAlgo)
                    .certUrl(certUrl)
                    .transmissionId(transmissionId)
                    .transmissionSig(transmissionSig)
                    .transmissionTime(transmissionTime)
                    .webhookId(properties.getWebhookId())
                    .webhookEvent(webhookEvent)
                    .build();

            PaypalWebhookVerifyResponse response = restClient.post()
                    .uri(properties.getBaseUrl() + "/v1/notifications/verify-webhook-signature")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(verifyRequest)
                    .retrieve()
                    .body(PaypalWebhookVerifyResponse.class);

            if (response == null) {
                log.warn("PayPal verify-webhook-signature returned null response");
                return false;
            }

            boolean valid = "SUCCESS".equals(response.getVerificationStatus());
            if (!valid) {
                log.warn("PayPal webhook signature invalid: verificationStatus={}", response.getVerificationStatus());
            }
            return valid;

        } catch (RestClientException e) {
            log.error("PayPal verify-webhook-signature API call failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error during PayPal webhook signature verification: {}", e.getMessage(), e);
            return false;
        }
    }
}
