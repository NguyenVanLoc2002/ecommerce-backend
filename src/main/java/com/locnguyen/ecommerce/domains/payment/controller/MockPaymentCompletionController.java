package com.locnguyen.ecommerce.domains.payment.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.payment.dto.PaymentCallbackRequest;
import com.locnguyen.ecommerce.domains.payment.dto.PaymentResponse;
import com.locnguyen.ecommerce.domains.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only endpoint that simulates a payment gateway redirecting the user back
 * after a mock payment.
 *
 * <p>Enabled only when {@code app.payment.mock.enabled=true} — never registered
 * in production.
 *
 * <p><strong>NEVER enable this in production.</strong>
 */
@Tag(name = "Mock Payment", description = "Dev-only mock payment completion (never in production)")
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConstants.API_V1 + "/payments/mock")
@ConditionalOnProperty(name = "app.payment.mock.enabled", havingValue = "true")
public class MockPaymentCompletionController {

    private final PaymentService paymentService;

    @Operation(
            summary = "[DEV] Simulate payment completion",
            description = "Accepts mock gateway redirect parameters and processes the callback via the " +
                    "standard payment flow. Equivalent to the gateway calling the callback endpoint. " +
                    "Active only when app.payment.mock.enabled=true."
    )
    @GetMapping("/complete")
    public ApiResponse<PaymentResponse> complete(
            @RequestParam String orderCode,
            @RequestParam String providerTxnId,
            @RequestParam(defaultValue = "SUCCESS") String status) {

        PaymentCallbackRequest request = new PaymentCallbackRequest();
        request.setOrderCode(orderCode);
        request.setProviderTxnId(providerTxnId);
        request.setStatus(status);
        request.setProvider("MOCK");
        request.setPayload("{\"orderCode\":\"" + orderCode
                + "\",\"providerTxnId\":\"" + providerTxnId
                + "\",\"status\":\"" + status + "\"}");

        return ApiResponse.success(paymentService.processCallback(request));
    }
}
