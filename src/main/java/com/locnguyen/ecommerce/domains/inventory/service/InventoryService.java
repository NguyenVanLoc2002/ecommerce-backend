package com.locnguyen.ecommerce.domains.inventory.service;

import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.inventory.dto.AdjustStockRequest;
import com.locnguyen.ecommerce.domains.inventory.dto.InventoryFilter;
import com.locnguyen.ecommerce.domains.inventory.dto.InventoryListItemResponse;
import com.locnguyen.ecommerce.domains.inventory.dto.InventoryResponse;
import com.locnguyen.ecommerce.domains.inventory.dto.ReserveStockRequest;
import com.locnguyen.ecommerce.domains.inventory.dto.StockFilter;
import com.locnguyen.ecommerce.domains.inventory.dto.StockMovementResponse;
import com.locnguyen.ecommerce.domains.inventory.entity.InventoryReservation;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface InventoryService {

    PagedResponse<InventoryListItemResponse> getInventories(InventoryFilter filter, Pageable pageable);

    List<InventoryResponse> getInventoryByVariant(UUID variantId);

    List<InventoryResponse> getInventoryByWarehouse(UUID warehouseId);

    InventoryResponse getInventoryDetail(UUID variantId, UUID warehouseId);

    PagedResponse<StockMovementResponse> getStockMovements(StockFilter filter, Pageable pageable);

    StockMovementResponse importStock(UUID variantId, UUID warehouseId, int quantity, String note);

    StockMovementResponse exportStock(UUID variantId, UUID warehouseId, int quantity, String note);

    StockMovementResponse adjustStock(AdjustStockRequest request);

    StockMovementResponse returnStock(UUID variantId, UUID warehouseId, int quantity, String referenceType, String referenceId);

    InventoryReservation reserveStock(ReserveStockRequest request);

    void releaseStock(String referenceType, String referenceId);

    void releaseStockForVariant(String referenceType, String referenceId, UUID variantId);

    void completeOrder(String referenceType, String referenceId);

    int releaseExpiredReservations();
}
