package com.locnguyen.ecommerce.domains.payment.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.idempotency.service.IdempotencyService;
import com.locnguyen.ecommerce.domains.payment.dto.InitPaymentRequest;
import com.locnguyen.ecommerce.domains.payment.dto.PaymentCaptureRequest;
import com.locnguyen.ecommerce.domains.payment.dto.PaymentResponse;
import com.locnguyen.ecommerce.domains.payment.service.PaymentService;
import com.locnguyen.ecommerce.domains.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
@Tag(name = "Payment", description = "Payment operations for customers and gateway callbacks")
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConstants.API_V1 + "/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final UserService userService;
    private final IdempotencyService idempotencyService;

    // ─── Customer endpoints ─────────────────────────────────────────────────

    @Operation(summary = "Get payment for my order")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/order/{orderId}")
    public ApiResponse<PaymentResponse> getMyPayment(@PathVariable UUID orderId) {
        return ApiResponse.success(
                paymentService.getPaymentForCustomer(orderId, userService.getCurrentCustomer()));
    }

    @Operation(
            summary = "Capture online payment after provider approval",
            description = "Called by the frontend when returning from a payment provider " +
                    "(e.g., PayPal). Captures the authorized payment and marks the order as paid. " +
                    "Idempotent: returns existing PAID record if already captured."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/order/{orderId}/capture")
    public ApiResponse<PaymentResponse> captureOnlinePayment(
            @PathVariable UUID orderId,
            @Valid @RequestBody PaymentCaptureRequest request) {
        return ApiResponse.success(
                paymentService.captureOnlinePayment(orderId, userService.getCurrentCustomer(), request));
    }

    @Operation(
            summary = "Initiate online payment for my order",
            description = "Requires Idempotency-Key header. Same key + same payload returns existing " +
                    "payment record without re-initiating. " +
                    "Calling after a FAILED payment retries the payment."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/order/{orderId}/initiate")
    public ApiResponse<PaymentResponse> initiateOnlinePayment(
            @PathVariable UUID orderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String rawKey,
            @Valid @RequestBody(required = false) InitPaymentRequest request) {
        if (request == null) {
            request = new InitPaymentRequest();
        }
        String key = idempotencyService.validateKey(rawKey);
        return ApiResponse.created(
                paymentService.initiateOnlinePayment(
                        orderId, userService.getCurrentCustomer(), request, key));
    }

}
