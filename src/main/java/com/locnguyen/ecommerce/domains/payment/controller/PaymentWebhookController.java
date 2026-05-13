package com.locnguyen.ecommerce.domains.payment.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.payment.dto.PaymentResponse;
import com.locnguyen.ecommerce.domains.payment.service.PaymentWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Receives raw webhook payloads from payment gateways.
 *
 * <p>No authentication — external gateways call this endpoint. Security is
 * enforced through HMAC/signature verification inside {@link PaymentWebhookService}.
 *
 * <p>The raw request body is passed as a {@code String} so the signature can be
 * verified against the exact bytes received before any parsing occurs.
 */
@Tag(name = "Payment Webhooks", description = "Gateway webhook receivers")
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConstants.API_V1 + "/webhooks/payment")
public class PaymentWebhookController {

    private final PaymentWebhookService paymentWebhookService;

    @Operation(
            summary = "[Gateway] Receive webhook from payment provider",
            description = "Endpoint for payment gateways (MoMo, VNPay, ZaloPay, etc.) to notify payment " +
                    "results. No authentication required. Signature is verified internally. Idempotent on " +
                    "duplicate providerTxnId. Every call is logged regardless of outcome."
    )
    @PostMapping("/{provider}")
    public ApiResponse<PaymentResponse> handleWebhook(
            @PathVariable String provider,
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Signature", required = false) String signature) {
        return ApiResponse.success(
                paymentWebhookService.receiveWebhook(provider, rawBody, signature));
    }
}
