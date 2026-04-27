package com.locnguyen.ecommerce.domains.inventory.dto;

import com.locnguyen.ecommerce.domains.inventory.enums.WarehouseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Update warehouse request (partial update)")
public class UpdateWarehouseRequest {

    @Size(max = 100, message = "Name must not exceed 100 characters")
    @Schema(example = "Kho chính Hồ Chí Minh")
    private String name;

    @Size(max = 255)
    @Schema(example = "456 Lê Lợi, Quận 1, TP. HCM")
    private String location;

    @Schema(example = "ACTIVE", description = "ACTIVE or INACTIVE")
    private WarehouseStatus status;
}
