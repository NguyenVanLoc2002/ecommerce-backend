package com.locnguyen.ecommerce.domains.inventory.dto;

import com.locnguyen.ecommerce.domains.inventory.enums.StockMovementType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Adjust stock request — manual inventory adjustment")
public class AdjustStockRequest {

    @NotNull(message = "Variant ID is required")
    @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long variantId;

    @NotNull(message = "Warehouse ID is required")
    @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long warehouseId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    @Schema(example = "50", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer quantity;

    @NotNull(message = "Movement type is required")
    @Schema(example = "IMPORT", description = "IMPORT, EXPORT, ADJUSTMENT, RETURN",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private StockMovementType movementType;

    @Size(max = 500)
    @Schema(example = "Nhập hàng mới từ nhà cung cấp ABC")
    private String note;
}
