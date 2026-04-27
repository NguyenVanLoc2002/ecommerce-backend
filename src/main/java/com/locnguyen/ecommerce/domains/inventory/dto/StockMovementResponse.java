package com.locnguyen.ecommerce.domains.inventory.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.inventory.enums.StockMovementType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Stock movement response — audit trail entry")
public class StockMovementResponse {

    private final Long id;
    private final Long variantId;
    private final String variantName;
    private final String sku;
    private final Long warehouseId;
    private final String warehouseName;
    private final StockMovementType movementType;
    private final int quantity;
    private final String referenceType;
    private final String referenceId;
    private final String note;
    private final int beforeOnHand;
    private final int beforeReserved;
    private final int beforeAvailable;
    private final int afterOnHand;
    private final int afterReserved;
    private final int afterAvailable;
    private final String createdBy;
    private final LocalDateTime createdAt;
}
