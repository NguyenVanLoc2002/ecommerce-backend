package com.locnguyen.ecommerce.domains.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class InventoryFilter {

    private Long variantId;
    private Long warehouseId;
    private Long productId;

    private String sku;
    private String keyword;
    private String variantStatus;

    private Boolean outOfStock;
    private Boolean lowStock;
    private Integer lowStockThreshold;
}