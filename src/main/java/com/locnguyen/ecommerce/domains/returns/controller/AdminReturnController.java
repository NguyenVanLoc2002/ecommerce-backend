package com.locnguyen.ecommerce.domains.returns.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.returns.dto.ReturnDecisionRequest;
import com.locnguyen.ecommerce.domains.returns.dto.ReturnResponse;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnStatus;
import com.locnguyen.ecommerce.domains.returns.service.OrderReturnService;
import com.locnguyen.ecommerce.domains.payment.dto.RefundResponse;
import com.locnguyen.ecommerce.domains.returns.service.ReturnRefundService;
import jakarta.validation.Valid;
import com.locnguyen.ecommerce.domains.returns.dto.ReturnHandlingRequest;
import com.locnguyen.ecommerce.domains.returns.dto.ExchangeResponse;
import com.locnguyen.ecommerce.domains.returns.dto.ExchangeSelection;
import com.locnguyen.ecommerce.domains.returns.service.ExchangeService;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequestMapping(AppConstants.API_V1 + "/admin/orders/{orderId}/returns/{returnId}")
public class AdminReturnController {
    private final OrderReturnService returnService;
    private final ExchangeService exchangeService;
    private final ReturnRefundService returnRefundService;

    @PostMapping("/refund")
    public ApiResponse<RefundResponse> prepareRefund(@PathVariable UUID orderId, @PathVariable UUID returnId) {
        return ApiResponse.success(returnRefundService.prepare(orderId, returnId));
    }

    @PostMapping("/exchanges")
    public ApiResponse<ExchangeResponse> reserveExchange(@PathVariable UUID orderId, @PathVariable UUID returnId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody ExchangeSelection selection, Principal principal) {
        return ApiResponse.success(exchangeService.reserve(orderId, returnId, key, principal.getName(), selection));
    }

    @PostMapping("/exchanges/{exchangeId}/replacement")
    public ApiResponse<ExchangeResponse> createReplacement(@PathVariable UUID orderId, @PathVariable UUID returnId,
            @PathVariable UUID exchangeId) {
        return ApiResponse.success(exchangeService.createReplacement(orderId, returnId, exchangeId));
    }

    @PostMapping("/exchanges/{exchangeId}/cancel")
    public ApiResponse<ExchangeResponse> cancelExchange(@PathVariable UUID orderId, @PathVariable UUID returnId,
            @PathVariable UUID exchangeId, Principal principal) {
        return ApiResponse.success(exchangeService.cancel(orderId, returnId, exchangeId, principal.getName()));
    }

    @PostMapping("/verify-delivery")
    public ApiResponse<ReturnResponse> verifyDelivery(@PathVariable UUID orderId, @PathVariable UUID returnId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody ReturnDecisionRequest request, Principal principal) {
        return decide(orderId, returnId, key, request, principal, ReturnStatus.REQUESTED);
    }

    @PostMapping("/approve")
    public ApiResponse<ReturnResponse> approve(@PathVariable UUID orderId, @PathVariable UUID returnId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody ReturnDecisionRequest request, Principal principal) {
        return decide(orderId, returnId, key, request, principal, ReturnStatus.APPROVED);
    }

    @PostMapping("/reject")
    public ApiResponse<ReturnResponse> reject(@PathVariable UUID orderId, @PathVariable UUID returnId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody ReturnDecisionRequest request, Principal principal) {
        return decide(orderId, returnId, key, request, principal, ReturnStatus.REJECTED);
    }

    @PostMapping("/receive")
    public ApiResponse<ReturnResponse> receive(@PathVariable UUID orderId, @PathVariable UUID returnId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody ReturnHandlingRequest request, Principal principal) {
        return ApiResponse.success(returnService.handle(orderId, returnId, key,
                principal.getName(), ReturnStatus.RECEIVED, request));
    }

    @PostMapping("/inspect")
    public ApiResponse<ReturnResponse> inspect(@PathVariable UUID orderId, @PathVariable UUID returnId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody ReturnHandlingRequest request, Principal principal) {
        return ApiResponse.success(returnService.handle(orderId, returnId, key,
                principal.getName(), ReturnStatus.INSPECTED, request));
    }

    private ApiResponse<ReturnResponse> decide(UUID orderId, UUID returnId, String key,
            ReturnDecisionRequest request, Principal principal, ReturnStatus next) {
        return ApiResponse.success(returnService.decide(orderId, returnId, key, principal.getName(), next, request));
    }
}
