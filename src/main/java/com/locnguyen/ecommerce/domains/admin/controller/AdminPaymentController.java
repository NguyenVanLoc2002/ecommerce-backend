package com.locnguyen.ecommerce.domains.admin.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.payment.dto.PaymentFilter;
import com.locnguyen.ecommerce.domains.payment.dto.PaymentResponse;
import com.locnguyen.ecommerce.domains.payment.dto.RefundRequest;
import com.locnguyen.ecommerce.domains.payment.dto.RefundResponse;
import com.locnguyen.ecommerce.domains.payment.dto.TransactionResponse;
import com.locnguyen.ecommerce.domains.payment.dto.WebhookLogResponse;
import com.locnguyen.ecommerce.domains.payment.service.PaymentRefundService;
import com.locnguyen.ecommerce.domains.payment.service.PaymentService;
import com.locnguyen.ecommerce.domains.payment.service.PaymentWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin Payment", description = "Admin payment management")
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConstants.API_V1 + "/admin/payments")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'STAFF')")
public class AdminPaymentController {

    private final PaymentService paymentService;
    private final PaymentRefundService paymentRefundService;
    private final PaymentWebhookService paymentWebhookService;

    @Operation(summary = "[Admin] List all payments (paginated, filterable)")
    @GetMapping
    public ApiResponse<PagedResponse<PaymentResponse>> getPayments(
            PaymentFilter filter,
            @PageableDefault(size = AppConstants.DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ApiResponse.success(paymentService.getPayments(filter, pageable));
    }

    @Operation(summary = "[Admin] Get payment by ID")
    @GetMapping("/{id}")
    public ApiResponse<PaymentResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success(paymentService.adminGetById(id));
    }

    @Operation(summary = "[Admin] Get payment by payment code")
    @GetMapping("/code/{code}")
    public ApiResponse<PaymentResponse> getByCode(@PathVariable String code) {
        return ApiResponse.success(paymentService.adminGetByCode(code));
    }

    @Operation(summary = "[Admin] Get payment by order ID")
    @GetMapping("/order/{orderId}")
    public ApiResponse<PaymentResponse> getByOrderId(@PathVariable UUID orderId) {
        return ApiResponse.success(paymentService.adminGetByOrderId(orderId));
    }

    @Operation(
            summary = "[Admin] Mark COD payment as received",
            description = "Marks a COD payment as PAID and syncs order.paymentStatus. " +
                    "Call this when the delivery agent confirms cash collected. " +
                    "Idempotent: calling again on an already-PAID payment is a no-op."
    )
    @PostMapping("/order/{orderId}/complete")
    public ApiResponse<PaymentResponse> completeCodPayment(@PathVariable UUID orderId) {
        return ApiResponse.success(paymentService.completeCodPayment(orderId));
    }

    @Operation(summary = "[Admin] Get transaction audit trail for a payment")
    @GetMapping("/{id}/transactions")
    public ApiResponse<List<TransactionResponse>> getTransactions(@PathVariable UUID id) {
        return ApiResponse.success(paymentService.getTransactions(id));
    }

    // ─── Refund operations ────────────────────────────────────────────────────

    @Operation(
            summary = "[Admin] Initiate a refund for a payment",
            description = "Creates a PENDING refund record. Partial refunds are supported. " +
                    "Requires ADMIN or SUPER_ADMIN role."
    )
    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<RefundResponse> initiateRefund(
            @PathVariable UUID id,
            @Valid @RequestBody RefundRequest request) {
        return ApiResponse.created(paymentRefundService.initiateRefund(id, request));
    }

    @Operation(summary = "[Admin] List all refunds for a payment")
    @GetMapping("/{id}/refunds")
    public ApiResponse<List<RefundResponse>> getRefunds(@PathVariable UUID id) {
        return ApiResponse.success(paymentRefundService.getRefundsForPayment(id));
    }

    @Operation(
            summary = "[Admin] Complete a pending refund",
            description = "Marks a PENDING refund as COMPLETED after funds are confirmed transferred. " +
                    "Idempotent: calling again on an already-COMPLETED refund is a no-op. " +
                    "Requires ADMIN or SUPER_ADMIN role."
    )
    @PostMapping("/refunds/{refundCode}/complete")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<RefundResponse> completeRefund(
            @PathVariable String refundCode,
            @RequestParam(required = false) String providerRefundId) {
        return ApiResponse.success(paymentRefundService.completeRefund(refundCode, providerRefundId));
    }

    // ─── Webhook log operations ───────────────────────────────────────────────

    @Operation(summary = "[Admin] List webhook logs for a payment")
    @GetMapping("/{id}/webhook-logs")
    public ApiResponse<List<WebhookLogResponse>> getWebhookLogs(@PathVariable UUID id) {
        return ApiResponse.success(paymentWebhookService.getLogsForPayment(id));
    }
}
