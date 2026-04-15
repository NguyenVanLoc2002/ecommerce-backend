package com.locnguyen.ecommerce.domains.payment.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.payment.dto.PaymentFilter;
import com.locnguyen.ecommerce.domains.payment.dto.PaymentResponse;
import com.locnguyen.ecommerce.domains.payment.dto.TransactionResponse;
import com.locnguyen.ecommerce.domains.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin Payment", description = "Admin payment management")
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConstants.API_V1 + "/admin/payments")
public class AdminPaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "[Admin] List all payments (paginated, filterable)")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ApiResponse<PagedResponse<PaymentResponse>> listPayments(
            PaymentFilter filter, Pageable pageable) {
        return ApiResponse.success(paymentService.listPayments(filter, pageable));
    }

    @Operation(summary = "[Admin] Get payment by ID")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}")
    public ApiResponse<PaymentResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(paymentService.adminGetById(id));
    }

    @Operation(summary = "[Admin] Get payment by payment code")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/code/{code}")
    public ApiResponse<PaymentResponse> getByCode(@PathVariable String code) {
        return ApiResponse.success(paymentService.adminGetByCode(code));
    }

    @Operation(summary = "[Admin] Get payment by order ID")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/order/{orderId}")
    public ApiResponse<PaymentResponse> getByOrderId(@PathVariable Long orderId) {
        return ApiResponse.success(paymentService.adminGetByOrderId(orderId));
    }

    @Operation(
            summary = "[Admin] Mark COD payment as received",
            description = "Marks a COD payment as PAID and syncs order.paymentStatus. " +
                    "Call this when the delivery agent confirms cash collected. " +
                    "Idempotent: calling again on an already-PAID payment is a no-op."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/order/{orderId}/complete")
    public ApiResponse<PaymentResponse> completeCodPayment(@PathVariable Long orderId) {
        return ApiResponse.success(paymentService.completeCodPayment(orderId));
    }

    @Operation(summary = "[Admin] Get transaction audit trail for a payment")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}/transactions")
    public ApiResponse<List<TransactionResponse>> getTransactions(@PathVariable Long id) {
        return ApiResponse.success(paymentService.getTransactions(id));
    }
}
