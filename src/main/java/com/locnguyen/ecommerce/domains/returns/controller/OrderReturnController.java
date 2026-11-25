package com.locnguyen.ecommerce.domains.returns.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.returns.dto.CreateReturnRequest;
import com.locnguyen.ecommerce.domains.returns.dto.CustomerReturnDetail;
import com.locnguyen.ecommerce.domains.returns.dto.CustomerReturnsResponse;
import com.locnguyen.ecommerce.domains.returns.dto.ReturnResponse;
import com.locnguyen.ecommerce.domains.returns.service.OrderReturnService;
import com.locnguyen.ecommerce.domains.user.service.UserService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(AppConstants.API_V1 + "/orders/{orderId}/returns")
public class OrderReturnController {
    private final OrderReturnService returnService;
    private final UserService userService;

    @GetMapping
    public ApiResponse<CustomerReturnsResponse> list(@PathVariable UUID orderId) {
        return ApiResponse.success(returnService.listForCustomer(orderId, userService.getCurrentCustomer()));
    }

    @GetMapping("/{returnId}")
    public ApiResponse<CustomerReturnDetail> detail(@PathVariable UUID orderId, @PathVariable UUID returnId) {
        return ApiResponse.success(returnService.getForCustomer(orderId, returnId, userService.getCurrentCustomer()));
    }

    @PostMapping
    public ApiResponse<ReturnResponse> create(@PathVariable UUID orderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody CreateReturnRequest request) {
        return ApiResponse.created(returnService.create(orderId, userService.getCurrentCustomer(), key, request));
    }
}
