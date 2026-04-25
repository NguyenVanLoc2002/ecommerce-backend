package com.locnguyen.ecommerce.domains.shipment.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.shipment.dto.*;
import com.locnguyen.ecommerce.domains.shipment.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin — Shipments", description = "Shipment management and tracking (admin only)")
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConstants.API_V1 + "/admin/shipments")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'STAFF')")
public class AdminShipmentController {

    private final ShipmentService shipmentService;

    @Operation(
            summary = "Create a shipment for an order",
            description = "Order must be in PROCESSING status. Transitions the order to SHIPPED."
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<ShipmentResponse> create(@Valid @RequestBody CreateShipmentRequest request) {
        return ApiResponse.created(shipmentService.createShipment(request));
    }

    @Operation(summary = "Get a shipment by ID")
    @GetMapping("/{id}")
    public ApiResponse<ShipmentResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(shipmentService.getById(id));
    }

    @Operation(summary = "Get a shipment by order ID")
    @GetMapping("/order/{orderId}")
    public ApiResponse<ShipmentResponse> getByOrderId(@PathVariable Long orderId) {
        return ApiResponse.success(shipmentService.getByOrderId(orderId));
    }

    @Operation(summary = "List shipments (paginated, filterable)")
    @GetMapping
    public ApiResponse<PagedResponse<ShipmentResponse>> getShipments(
            ShipmentFilter filter,
            @PageableDefault(size = AppConstants.DEFAULT_PAGE_SIZE, sort = "createdAt") Pageable pageable) {
        return ApiResponse.success(shipmentService.getShipments(filter, pageable));
    }

    @Operation(summary = "Update shipment details (carrier, tracking number, dates, note)")
    @PatchMapping("/{id}")
    public ApiResponse<ShipmentResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateShipmentRequest request) {
        return ApiResponse.success(shipmentService.updateShipment(id, request));
    }

    @Operation(
            summary = "Advance shipment status and record a tracking event",
            description = "Validates the status transition. On DELIVERED, transitions the order to DELIVERED."
    )
    @PatchMapping("/{id}/status")
    public ApiResponse<ShipmentResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateShipmentStatusRequest request) {
        return ApiResponse.success(shipmentService.updateStatus(id, request));
    }
}
