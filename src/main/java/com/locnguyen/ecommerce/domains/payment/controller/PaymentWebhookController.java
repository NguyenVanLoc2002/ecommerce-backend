package com.locnguyen.ecommerce.domains.payment.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.domains.payment.service.PaymentWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Receives raw webhook payloads from payment gateways.
 *
 * <p>No authentication — external gateways call this endpoint. Security is
 * enforced through HMAC/signature verification inside {@link PaymentWebhookService}.
 *
 * <p>The raw request body is passed as a {@code String} so the signature can be
 * verified against the exact bytes received before any parsing occurs.
 *
 * <p>PayPal requires a dedicated endpoint to capture its five signature headers.
 * Spring MVC resolves {@code /PAYPAL} before the generic {@code /{provider}} wildcard.
 */
@Tag(name = "Payment Webhooks", description = "Gateway webhook receivers")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConstants.API_V1 + "/webhooks/payment")
public class PaymentWebhookController {

    private final PaymentWebhookService paymentWebhookService;
    private final ObjectMapper objectMapper;

    @Operation(
            summary = "[PayPal] Receive PayPal webhook notification",
            description = "PayPal-specific endpoint that captures the five PayPal signature headers " +
                    "required for webhook verification via the PayPal Webhooks API v1. " +
                    "Returns HTTP 200 as required by PayPal."
    )
    @PostMapping("/PAYPAL")
    public void handlePaypalWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "PAYPAL-AUTH-ALGO", required = false) String authAlgo,
            @RequestHeader(value = "PAYPAL-CERT-URL", required = false) String certUrl,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-ID", required = false) String transmissionId,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-SIG", required = false) String transmissionSig,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-TIME", required = false) String transmissionTime) {

        String headersJson = buildHeadersJson(authAlgo, certUrl, transmissionId, transmissionSig, transmissionTime);
        paymentWebhookService.receiveWebhook("PAYPAL", rawBody, headersJson);
    }

    @Operation(
            summary = "[Gateway] Receive IPN from payment provider",
            description = "Endpoint for payment gateways (MoMo, VNPay, ZaloPay, etc.) to notify payment " +
                    "results. No authentication required. Signature is verified internally. Idempotent on " +
                    "duplicate providerTxnId. Every call is logged regardless of outcome. " +
                    "Returns 204 No Content as required by MoMo IPN spec."
    )
    @PostMapping("/{provider}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void handleWebhook(
            @PathVariable String provider,
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Signature", required = false) String signature) {
        paymentWebhookService.receiveWebhook(provider, rawBody, signature);
    }

    private String buildHeadersJson(String authAlgo, String certUrl, String transmissionId,
                                    String transmissionSig, String transmissionTime) {
        Map<String, String> headersMap = new LinkedHashMap<>();
        headersMap.put("paypal_auth_algo", authAlgo != null ? authAlgo : "");
        headersMap.put("paypal_cert_url", certUrl != null ? certUrl : "");
        headersMap.put("paypal_transmission_id", transmissionId != null ? transmissionId : "");
        headersMap.put("paypal_transmission_sig", transmissionSig != null ? transmissionSig : "");
        headersMap.put("paypal_transmission_time", transmissionTime != null ? transmissionTime : "");
        try {
            return objectMapper.writeValueAsString(headersMap);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize PayPal webhook headers — proceeding with null signature");
            return null;
        }
    }
}
