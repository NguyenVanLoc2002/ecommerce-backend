package com.locnguyen.ecommerce.domains.inventory.dto;

import com.locnguyen.ecommerce.domains.inventory.enums.StockMovementType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Stock movement filter parameters")
public class StockFilter {

    @Schema(description = "Filter by variant ID")
    private Long variantId;

    @Schema(description = "Filter by warehouse ID")
    private Long warehouseId;

    @Schema(description = "Filter by movement type (IMPORT, EXPORT, ADJUSTMENT, RETURN)")
    private StockMovementType movementType;
}
