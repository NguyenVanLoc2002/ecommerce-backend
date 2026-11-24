package com.locnguyen.ecommerce.domains.product.dto;

import com.locnguyen.ecommerce.domains.returns.enums.ReturnPolicy;

import com.locnguyen.ecommerce.domains.product.enums.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

import java.util.UUID;
@Data
@Schema(description = "Create product request")
public class CreateProductRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255)
    @Schema(example = "Áo thun basic nam", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    private ReturnPolicy returnPolicy;

    @NotBlank(message = "Slug is required")
    @Size(max = 255)
    @Schema(example = "ao-thun-basic-nam", requiredMode = Schema.RequiredMode.REQUIRED)
    private String slug;

    @Size(max = 500)
    @Schema(example = "Áo thun cotton cơ bản, phù hợp mọi dịp")
    private String shortDescription;

    private String description;

    @Schema(description = "Brand ID", example = "1")
    private UUID brandId;

    @Schema(description = "Category IDs")
    private List<UUID> categoryIds;

    @Schema(description = "Product status (DRAFT not visible to public)", example = "DRAFT")
    private ProductStatus status;

    @Schema(description = "Mark as featured product", example = "false")
    private Boolean featured;
}
