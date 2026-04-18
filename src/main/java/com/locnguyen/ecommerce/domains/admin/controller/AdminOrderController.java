package com.locnguyen.ecommerce.domains.admin.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.admin.dto.AdminOrderListItemResponse;
import com.locnguyen.ecommerce.domains.order.dto.OrderAdminFilter;
import com.locnguyen.ecommerce.domains.order.dto.OrderResponse;
import com.locnguyen.ecommerce.domains.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    public ApiResponse<OrderResponse> getOrderById(@PathVariable Long id) {
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
    public ApiResponse<OrderResponse> confirmOrder(@PathVariable Long id) {
        return ApiResponse.success(orderService.confirmOrder(id));
    }

    @Operation(summary = "Mark order as PROCESSING — CONFIRMED → PROCESSING")
    @PostMapping("/{id}/process")
    public ApiResponse<OrderResponse> processOrder(@PathVariable Long id) {
        return ApiResponse.success(orderService.processOrder(id));
    }

    @Operation(
        summary = "Mark order as DELIVERED — SHIPPED → DELIVERED",
        description = "Note: SHIPPED status is set automatically when a shipment is created via the shipment API."
    )
    @PostMapping("/{id}/deliver")
    public ApiResponse<OrderResponse> deliverOrder(@PathVariable Long id) {
        return ApiResponse.success(orderService.deliverOrder(id));
    }

    @Operation(summary = "Complete order — DELIVERED → COMPLETED (commits reserved stock)")
    @PostMapping("/{id}/complete")
    public ApiResponse<OrderResponse> completeOrder(@PathVariable Long id) {
        return ApiResponse.success(orderService.completeOrder(id));
    }

    @Operation(
        summary = "Cancel order — releases reserved stock",
        description = "Cancellable from PENDING, AWAITING_PAYMENT, or CONFIRMED. Requires ADMIN or above."
    )
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<OrderResponse> cancelOrder(@PathVariable Long id) {
        return ApiResponse.success(orderService.cancelOrder(id));
    }
}
