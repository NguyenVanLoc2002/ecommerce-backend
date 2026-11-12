package com.locnguyen.ecommerce.domains.inventory.mapper;

import com.locnguyen.ecommerce.domains.inventory.dto.InventoryResponse;
import com.locnguyen.ecommerce.domains.inventory.dto.InventoryListItemResponse;
import com.locnguyen.ecommerce.domains.inventory.dto.StockMovementResponse;
import com.locnguyen.ecommerce.domains.inventory.entity.Inventory;
import com.locnguyen.ecommerce.domains.inventory.entity.StockMovement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InventoryMapper {

    @Mapping(target = "variantId", source = "variant.id")
    @Mapping(target = "variantName", source = "variant.variantName")
    @Mapping(target = "sku", source = "variant.sku")
    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "warehouseName", source = "warehouse.name")
    @Mapping(target = "available", expression = "java(inventory.getOnHand() - inventory.getReserved())")
    InventoryResponse toResponse(Inventory inventory);

    @Mapping(target = "productId", source = "variant.product.id")
    @Mapping(target = "productName", source = "variant.product.name")
    @Mapping(target = "variantId", source = "variant.id")
    @Mapping(target = "variantName", source = "variant.variantName")
    @Mapping(target = "sku", source = "variant.sku")
    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "warehouseName", source = "warehouse.name")
    @Mapping(target = "available", expression = "java(inventory.getOnHand() - inventory.getReserved())")
    InventoryListItemResponse toListItemResponse(Inventory inventory);

    @Mapping(target = "variantId", source = "variant.id")
    @Mapping(target = "variantName", source = "variant.variantName")
    @Mapping(target = "sku", source = "variant.sku")
    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "warehouseName", source = "warehouse.name")
    StockMovementResponse toResponse(StockMovement movement);
}
