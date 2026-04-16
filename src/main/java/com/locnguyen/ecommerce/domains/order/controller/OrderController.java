package com.locnguyen.ecommerce.domains.order.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.order.dto.CreateOrderRequest;
import com.locnguyen.ecommerce.domains.order.dto.OrderListItemResponse;
import com.locnguyen.ecommerce.domains.order.dto.OrderResponse;
import com.locnguyen.ecommerce.domains.order.service.OrderService;
import com.locnguyen.ecommerce.domains.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Order", description = "Order management")
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConstants.API_V1 + "/orders")
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;

    @Operation(summary = "Create order from cart")
    @SecurityRequirement(name = "bearerAuth")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.created(orderService.createOrder(userService.getCurrentCustomer(), request));
    }

    @Operation(summary = "List my orders (paginated)")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ApiResponse<PagedResponse<OrderListItemResponse>> getMyOrders(Pageable pageable) {
        return ApiResponse.success(orderService.getMyOrders(userService.getCurrentCustomer(), pageable));
    }

    @Operation(summary = "Get order by ID")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrderById(@PathVariable Long id) {
        return ApiResponse.success(orderService.getOrderById(id, userService.getCurrentCustomer()));
    }

    @Operation(summary = "Confirm order (admin/staff only)")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'STAFF')")
    @PostMapping("/{id}/confirm")
    public ApiResponse<OrderResponse> confirmOrder(@PathVariable Long id) {
        return ApiResponse.success(orderService.confirmOrder(id));
    }

    @Operation(summary = "Cancel order (admin/staff only — customers use /my/{id}/cancel)")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'STAFF')")
    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancelOrder(@PathVariable Long id) {
        return ApiResponse.success(orderService.cancelOrder(id));
    }

    @Operation(summary = "Complete order (admin/staff only)")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'STAFF')")
    @PostMapping("/{id}/complete")
    public ApiResponse<OrderResponse> completeOrder(@PathVariable Long id) {
        return ApiResponse.success(orderService.completeOrder(id));
    }

    @Operation(summary = "Cancel my own order",
               description = "Customers may cancel their own order if it is still PENDING or AWAITING_PAYMENT.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/my/{id}/cancel")
    public ApiResponse<OrderResponse> cancelMyOrder(@PathVariable Long id) {
        return ApiResponse.success(
                orderService.cancelMyOrder(id, userService.getCurrentCustomer()));
    }
}
