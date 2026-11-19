package com.locnguyen.ecommerce.domains.admin.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.admin.dto.AdminOrderListItemResponse;
import com.locnguyen.ecommerce.domains.order.dto.OrderAdminFilter;
import com.locnguyen.ecommerce.domains.order.dto.OrderResponse;
import com.locnguyen.ecommerce.domains.order.service.OrderService;
import com.locnguyen.ecommerce.domains.shipment.dto.CancelOrderRequest;
import com.locnguyen.ecommerce.domains.shipment.dto.CancellationOperationResponse;
import com.locnguyen.ecommerce.domains.shipment.dto.ReconcileCancellationRequest;
import com.locnguyen.ecommerce.domains.shipment.service.OrderCancellationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.UUID;
/**
 * Admin order management API.
 *
 * <p>URL-level security ({@code /api/v1/admin/**}) allows STAFF and above.
 * Destructive or financial operations use {@code @PreAuthorize} for additional
 * role enforcement at the method level.
 *
 * <p>All business logic is delegated to {@link OrderService}.
 */
@Tag(name = "Admin — Order", description = "Order management for admins and staff")
@RestController
@RequestMapping(AppConstants.API_V1 + "/admin/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'STAFF')")
public class AdminOrderController {

    private final OrderService orderService;
    private final OrderCancellationService orderCancellationService;

    // ─── Read operations ──────────────────────────────────────────────────────

    @Operation(summary = "List all orders with optional filters")
    @GetMapping
    public ApiResponse<PagedResponse<AdminOrderListItemResponse>> getAllOrders(
            OrderAdminFilter filter,
            @PageableDefault(size = AppConstants.DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ApiResponse.success(orderService.getAllOrders(filter, pageable));
    }

    @Operation(summary = "Get order detail by ID (no ownership check)")
    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrderById(@PathVariable UUID id) {
        return ApiResponse.success(orderService.getOrderByIdAdmin(id));
    }

    @Operation(summary = "Get order detail by order code")
    @GetMapping("/code/{orderCode}")
    public ApiResponse<OrderResponse> getOrderByCode(@PathVariable String orderCode) {
        return ApiResponse.success(orderService.getOrderByCode(orderCode));
    }

    // ─── Status transitions ───────────────────────────────────────────────────

    @Operation(summary = "Confirm order — PENDING / AWAITING_PAYMENT → CONFIRMED")
    @PostMapping("/{id}/confirm")
    public ApiResponse<OrderResponse> confirmOrder(@PathVariable UUID id) {
        return ApiResponse.success(orderService.confirmOrder(id));
    }

    @Operation(summary = "Mark order as PROCESSING — CONFIRMED → PROCESSING")
    @PostMapping("/{id}/process")
    public ApiResponse<OrderResponse> processOrder(@PathVariable UUID id) {
        return ApiResponse.success(orderService.processOrder(id));
    }

    @Operation(
        summary = "Mark order as DELIVERED — SHIPPED → DELIVERED",
        description = "Note: SHIPPED status is set automatically when a shipment is created via the shipment API."
    )
    @PostMapping("/{id}/deliver")
    public ApiResponse<OrderResponse> deliverOrder(@PathVariable UUID id) {
        return ApiResponse.success(orderService.deliverOrder(id));
    }

    @Operation(summary = "Complete order — DELIVERED → COMPLETED (commits reserved stock)")
    @PostMapping("/{id}/complete")
    public ApiResponse<OrderResponse> completeOrder(@PathVariable UUID id) {
        return ApiResponse.success(orderService.completeOrder(id));
    }

    @Operation(summary = "Request order and shipment cancellation")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<CancellationOperationResponse> cancelOrder(
            @PathVariable UUID id, @Valid @RequestBody CancelOrderRequest request) {
        return ApiResponse.success(orderCancellationService.cancelOrder(id, request));
    }

    @Operation(summary = "Manually reconcile an unknown order cancellation")
    @PostMapping("/{id}/cancellation/reconcile")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<CancellationOperationResponse> reconcileCancellation(
            @PathVariable UUID id, @Valid @RequestBody ReconcileCancellationRequest request) {
        return ApiResponse.success(orderCancellationService.reconcile(id, request));
    }

    @Operation(summary = "Get the cancellation and reconciliation state for an order")
    @GetMapping("/{id}/cancellation")
    public ApiResponse<CancellationOperationResponse> getCancellation(@PathVariable UUID id) {
        return ApiResponse.success(orderCancellationService.getByOrderId(id));
    }
}
