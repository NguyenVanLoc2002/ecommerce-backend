package com.locnguyen.ecommerce.domains.inventory.dto;

import com.locnguyen.ecommerce.domains.inventory.enums.WarehouseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Update warehouse request (partial update)")
public class UpdateWarehouseRequest {

    @Size(max = 100, message = "Name must not exceed 100 characters")
    @Schema(example = "Kho chính Hồ Chí Minh")
    private String name;

    @Size(max = 255)
    @Schema(example = "456 Lê Lợi, Quận 1, TP. HCM")
    private String location;

    @Size(max = 500)
    private String pickupAddress;

    @Size(max = 255)
    private String pickupShortAddress;

    @Size(max = 200)
    private String pickupContactName;

    @Size(max = 50)
    private String pickupContactPhone;

    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private BigDecimal pickupLat;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private BigDecimal pickupLng;

    @Schema(example = "ACTIVE", description = "ACTIVE or INACTIVE")
    private WarehouseStatus status;
}
