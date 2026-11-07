package com.locnguyen.ecommerce.domains.inventory.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Validated warehouse-owned pickup details for carrier provider requests.
 */
public record WarehousePickupLocation(
        UUID warehouseId,
        String address,
        String shortAddress,
        String contactName,
        String contactPhone,
        BigDecimal latitude,
        BigDecimal longitude
) {}
