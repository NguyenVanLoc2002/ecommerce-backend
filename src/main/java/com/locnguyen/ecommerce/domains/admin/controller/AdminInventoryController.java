package com.locnguyen.ecommerce.domains.admin.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.inventory.dto.*;
import com.locnguyen.ecommerce.domains.inventory.entity.InventoryReservation;
import com.locnguyen.ecommerce.domains.inventory.enums.StockMovementType;
import com.locnguyen.ecommerce.domains.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin inventory management API.
 *
 * <p>Read operations are accessible to STAFF and above.
 * Mutating operations (adjust stock, reserve, release) require ADMIN or above.
 *
 * <p>Warehouse management lives in {@link AdminWarehouseController}.
 */
@Tag(name = "Admin — Inventory", description = "Inventory management")
@RestController
@RequestMapping(AppConstants.API_V1 + "/admin/inventories")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'STAFF')")
public class AdminInventoryController {

    private final InventoryService inventoryService;

    // ─── Inventory view ───────────────────────────────────────────────────────

    @Operation(summary = "List inventories (paginated, filterable)")
    @GetMapping
    public ApiResponse<PagedResponse<InventoryResponse>> getInventories(
            InventoryFilter filter,
            @PageableDefault(
                    size = AppConstants.DEFAULT_PAGE_SIZE,
                    sort = "updatedAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable) {
        return ApiResponse.success(inventoryService.getInventories(filter, pageable));
    }

    @Operation(summary = "Get inventory levels for a variant (all warehouses)")
    @GetMapping("/variant/{variantId}")
    public ApiResponse<List<InventoryResponse>> getInventoryByVariant(@PathVariable Long variantId) {
        return ApiResponse.success(inventoryService.getInventoryByVariant(variantId));
    }

    @Operation(summary = "Get all inventory in a warehouse")
    @GetMapping("/warehouse/{warehouseId}")
    public ApiResponse<List<InventoryResponse>> getInventoryByWarehouse(@PathVariable Long warehouseId) {
        return ApiResponse.success(inventoryService.getInventoryByWarehouse(warehouseId));
    }

    @Operation(summary = "Get inventory detail for a specific variant + warehouse combination")
    @GetMapping("/variant/{variantId}/warehouse/{warehouseId}")
    public ApiResponse<InventoryResponse> getInventoryDetail(
            @PathVariable Long variantId,
            @PathVariable Long warehouseId) {
        return ApiResponse.success(inventoryService.getInventoryDetail(variantId, warehouseId));
    }

    @Operation(summary = "Get stock movement history (filterable, paginated)")
    @GetMapping("/movements")
    public ApiResponse<PagedResponse<StockMovementResponse>> getStockMovements(
            @Parameter(description = "Filter by variant ID") @RequestParam(required = false) Long variantId,
            @Parameter(description = "Filter by warehouse ID") @RequestParam(required = false) Long warehouseId,
            @Parameter(description = "Filter by movement type (IMPORT, EXPORT, ADJUSTMENT, RETURN)")
            @RequestParam(required = false) StockMovementType movementType,
            @PageableDefault(size = AppConstants.DEFAULT_PAGE_SIZE) Pageable pageable) {
        StockFilter filter = new StockFilter();
        filter.setVariantId(variantId);
        filter.setWarehouseId(warehouseId);
        filter.setMovementType(movementType);
        return ApiResponse.success(inventoryService.getStockMovements(filter, pageable));
    }

    // ─── Stock operations ─────────────────────────────────────────────────────

    @Operation(
            summary = "Adjust stock — import, export, manual adjustment or return",
            description = "Use movementType IMPORT to receive goods, EXPORT for write-offs, ADJUSTMENT for corrections, RETURN for customer returns."
    )
    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<StockMovementResponse> adjustStock(
            @Valid @RequestBody AdjustStockRequest request) {
        return ApiResponse.success(inventoryService.adjustStock(request));
    }

    @Operation(summary = "Manually reserve stock for an order")
    @PostMapping("/reserve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<InventoryReservation> reserveStock(
            @Valid @RequestBody ReserveStockRequest request) {
        return ApiResponse.success(inventoryService.reserveStock(request));
    }

    @Operation(summary = "Release reserved stock by reference (order code)")
    @PostMapping("/release")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<Void> releaseStock(
            @RequestParam String referenceType,
            @RequestParam String referenceId) {
        inventoryService.releaseStock(referenceType, referenceId);
        return ApiResponse.noContent();
    }
}
