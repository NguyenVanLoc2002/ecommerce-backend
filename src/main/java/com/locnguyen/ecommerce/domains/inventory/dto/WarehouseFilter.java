package com.locnguyen.ecommerce.domains.inventory.dto;

import com.locnguyen.ecommerce.domains.inventory.enums.WarehouseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Warehouse list filters")
public class WarehouseFilter {

    @Schema(description = "Filter by warehouse status", example = "ACTIVE")
    private WarehouseStatus status;

    @Schema(description = "Soft delete filter: false=active only, true=deleted only", example = "false")
    private Boolean isDeleted;

    @Schema(description = "Include both active and deleted rows", example = "false")
    private Boolean includeDeleted;
}
