package com.locnguyen.ecommerce.domains.admin.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.inventory.dto.CreateWarehouseRequest;
import com.locnguyen.ecommerce.domains.inventory.dto.UpdateWarehouseRequest;
import com.locnguyen.ecommerce.domains.inventory.dto.WarehouseResponse;
import com.locnguyen.ecommerce.domains.inventory.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin warehouse management API.
 *
 * <p>Read operations are accessible to STAFF and above.
 * Mutating operations require ADMIN or above.
 */
@Tag(name = "Admin — Warehouse", description = "Warehouse management")
@RestController
@RequestMapping(AppConstants.API_V1 + "/admin/warehouses")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'STAFF')")
public class AdminWarehouseController {

    private final WarehouseService warehouseService;

    @Operation(summary = "List all active warehouses")
    @GetMapping
    public ApiResponse<List<WarehouseResponse>> listWarehouses() {
        return ApiResponse.success(warehouseService.getActiveWarehouses());
    }

    @Operation(summary = "Get warehouse by ID")
    @GetMapping("/{id}")
    public ApiResponse<WarehouseResponse> getWarehouse(@PathVariable Long id) {
        return ApiResponse.success(warehouseService.getWarehouseById(id));
    }

    @Operation(summary = "Create warehouse")
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<WarehouseResponse> createWarehouse(
            @Valid @RequestBody CreateWarehouseRequest request) {
        return ApiResponse.created(warehouseService.createWarehouse(request));
    }

    @Operation(summary = "Update warehouse")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<WarehouseResponse> updateWarehouse(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWarehouseRequest request) {
        return ApiResponse.success(warehouseService.updateWarehouse(id, request));
    }

    @Operation(summary = "Soft-delete warehouse")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<Void> deleteWarehouse(@PathVariable Long id) {
        warehouseService.deleteWarehouse(id);
        return ApiResponse.noContent();
    }
}
