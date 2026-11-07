package com.locnguyen.ecommerce.domains.inventory.service;

import com.locnguyen.ecommerce.domains.inventory.dto.CreateWarehouseRequest;
import com.locnguyen.ecommerce.domains.inventory.dto.UpdateWarehouseRequest;
import com.locnguyen.ecommerce.domains.inventory.dto.WarehouseFilter;
import com.locnguyen.ecommerce.domains.inventory.dto.WarehousePickupLocation;
import com.locnguyen.ecommerce.domains.inventory.dto.WarehouseResponse;
import com.locnguyen.ecommerce.domains.inventory.entity.Warehouse;

import java.util.List;
import java.util.UUID;

public interface WarehouseService {

    List<WarehouseResponse> getActiveWarehouses();

    List<WarehouseResponse> getWarehouses(WarehouseFilter filter);

    WarehouseResponse getWarehouseById(UUID id);

    WarehouseResponse createWarehouse(CreateWarehouseRequest request);

    WarehouseResponse updateWarehouse(UUID id, UpdateWarehouseRequest request);

    void deleteWarehouse(UUID id);

    WarehousePickupLocation getPickupLocation(UUID id);

    Warehouse findOrThrow(UUID id);
}
