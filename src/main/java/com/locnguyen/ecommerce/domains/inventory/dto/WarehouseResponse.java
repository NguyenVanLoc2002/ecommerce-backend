package com.locnguyen.ecommerce.domains.inventory.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.inventory.enums.WarehouseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

import java.math.BigDecimal;
import java.util.UUID;
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Warehouse response")
public class WarehouseResponse {

    private final UUID id;
    private final String name;
    private final String code;
    private final String location;
    private final String pickupAddress;
    private final String pickupShortAddress;
    private final String pickupContactName;
    private final String pickupContactPhone;
    private final BigDecimal pickupLat;
    private final BigDecimal pickupLng;
    private final WarehouseStatus status;
    private final LocalDateTime createdAt;
}
