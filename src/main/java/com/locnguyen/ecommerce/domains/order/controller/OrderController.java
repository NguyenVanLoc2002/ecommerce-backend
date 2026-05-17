package com.locnguyen.ecommerce.domains.order.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.idempotency.service.IdempotencyService;
import com.locnguyen.ecommerce.domains.order.dto.CreateOrderRequest;
import com.locnguyen.ecommerce.domains.order.dto.OrderFilter;
import com.locnguyen.ecommerce.domains.order.dto.OrderListItemResponse;
import com.locnguyen.ecommerce.domains.order.dto.OrderPreviewResponse;
import com.locnguyen.ecommerce.domains.order.dto.OrderResponse;
import com.locnguyen.ecommerce.domains.order.service.OrderService;
import com.locnguyen.ecommerce.domains.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
/**
 * Customer-facing order endpoints.
 * Admin order management lives in
 * {@link com.locnguyen.ecommerce.domains.admin.controller.AdminOrderController}.
 */
@Tag(name = "Order", description = "Customer order endpoints")
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConstants.API_V1 + "/orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;
    private final IdempotencyService idempotencyService;

    @Operation(summary = "Create order from cart",
               description = "Requires Idempotency-Key header. Same key + same payload returns existing order.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<OrderResponse> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String rawKey,
            @Valid @RequestBody CreateOrderRequest request) {
        String key = idempotencyService.validateKey(rawKey);
        return ApiResponse.created(
                orderService.createOrder(userService.getCurrentCustomer(), request, key));
    }

    @Operation(summary = "Preview checkout totals including shipping fee quote")
    @PostMapping("/preview")
    public ApiResponse<OrderPreviewResponse> previewOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.success(orderService.previewOrder(userService.getCurrentCustomer(), request));
    }

    @Operation(summary = "List my orders (paginated, filterable by status)")
    @GetMapping
    public ApiResponse<PagedResponse<OrderListItemResponse>> getMyOrders(
            OrderFilter filter,
            @PageableDefault(size = AppConstants.DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ApiResponse.success(orderService.getMyOrders(userService.getCurrentCustomer(), filter, pageable));
    }

    @Operation(summary = "Get my order by ID")
    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrderById(@PathVariable UUID id) {
        return ApiResponse.success(orderService.getOrderById(id, userService.getCurrentCustomer()));
    }

    @Operation(
        summary = "Cancel my own order",
        description = "Only allowed when status is PENDING or AWAITING_PAYMENT."
    )
    @PostMapping("/my/{id}/cancel")
    public ApiResponse<OrderResponse> cancelMyOrder(@PathVariable UUID id) {
        return ApiResponse.success(orderService.cancelMyOrder(id, userService.getCurrentCustomer()));
    }
}
