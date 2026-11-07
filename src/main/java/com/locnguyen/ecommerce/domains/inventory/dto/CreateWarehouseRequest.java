package com.locnguyen.ecommerce.domains.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Create warehouse request")
public class CreateWarehouseRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    @Schema(example = "Kho chính Hà Nội", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "Code is required")
    @Size(max = 50, message = "Code must not exceed 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Code must contain only letters, digits, hyphens, or underscores")
    @Schema(example = "KHO-HN-01", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @Size(max = 255)
    @Schema(example = "123 Nguyễn Văn Cừ, Long Biên, Hà Nội")
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
}
