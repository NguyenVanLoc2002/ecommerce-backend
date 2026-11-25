package com.locnguyen.ecommerce.infrastructure.payment.paypal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.payment.config.PaymentProviderConfigResolver;
import com.locnguyen.ecommerce.domains.payment.config.PaypalResolvedPaymentConfig;
import com.locnguyen.ecommerce.infrastructure.payment.PaymentRestClientFactory;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCaptureOrderResponse;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCreateOrderRequest;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCreateOrderResponse;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalWebhookVerifyRequest;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalWebhookVerifyResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaypalClient {

    private final PaymentProviderConfigResolver configResolver;
    private final PaypalOAuthClient oAuthClient;
    private final PaymentRestClientFactory restClientFactory;
    private final ObjectMapper objectMapper;

    public PaypalCreateOrderResponse createOrder(PaypalCreateOrderRequest request, String orderCode) {
        PaypalResolvedPaymentConfig config = paypal();
        String token = oAuthClient.getAccessToken();
        RestClient restClient = restClientFactory.create(config.connectTimeoutMs(), config.readTimeoutMs());

        try {
            PaypalCreateOrderResponse response = restClient.post()
                    .uri(config.baseUrl() + "/v2/checkout/orders")
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
            throw new AppException(ErrorCode.PAYMENT_FAILED, "PayPal API call failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error calling PayPal create-order API: orderCode={} error={}",
                    orderCode, e.getMessage(), e);
            throw new AppException(ErrorCode.PAYMENT_FAILED, "Unexpected error calling PayPal API");
        }
    }

    public PaypalCaptureOrderResponse captureOrder(String paypalOrderId) {
        PaypalResolvedPaymentConfig config = paypal();
        String token = oAuthClient.getAccessToken();
        String captureUrl = config.baseUrl() + "/v2/checkout/orders/" + paypalOrderId + "/capture";
        RestClient restClient = restClientFactory.create(config.connectTimeoutMs(), config.readTimeoutMs());

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
            throw new AppException(ErrorCode.PAYMENT_FAILED, "Unexpected error calling PayPal capture API");
        }
    }

    public boolean verifyWebhookSignature(
            String authAlgo, String certUrl, String transmissionId,
            String transmissionSig, String transmissionTime, String rawBody) {

        PaypalResolvedPaymentConfig config = paypal();
        if (config.webhookId() == null || config.webhookId().isBlank()) {
            log.warn("PayPal webhook verification skipped â€” webhook-id not configured");
            return false;
        }

        try {
            String token = oAuthClient.getAccessToken();
            RestClient restClient = restClientFactory.create(config.connectTimeoutMs(), config.readTimeoutMs());

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
                    .webhookId(config.webhookId())
                    .webhookEvent(webhookEvent)
                    .build();

            PaypalWebhookVerifyResponse response = restClient.post()
                    .uri(config.baseUrl() + "/v1/notifications/verify-webhook-signature")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(verifyRequest)
                    .retrieve()
                    .body(PaypalWebhookVerifyResponse.class);

            return response != null && "SUCCESS".equals(response.getVerificationStatus());
        } catch (RestClientException e) {
            log.error("PayPal verify-webhook-signature API call failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error during PayPal webhook signature verification: {}", e.getMessage(), e);
            return false;
        }
    }

    public com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalRefundDetails getRefund(String refundId) {
        PaypalResolvedPaymentConfig config = paypal();
        String token = oAuthClient.getAccessToken();
        try {
            var response = restClientFactory.create(config.connectTimeoutMs(), config.readTimeoutMs()).get()
                    .uri(config.baseUrl() + "/v2/payments/refunds/{refundId}", refundId)
                    .headers(headers -> headers.setBearerAuth(token))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalRefundDetails.class);
            if (response != null) return response;
        } catch (RestClientException ignored) {
            // Verification can be retried without submitting a second refund.
        }
        throw new AppException(ErrorCode.PAYMENT_FAILED, "PayPal refund details could not be verified");
    }

    private PaypalResolvedPaymentConfig paypal() {
        return configResolver.resolvePaypal();
    }
}
