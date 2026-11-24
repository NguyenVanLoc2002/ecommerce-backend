package com.locnguyen.ecommerce.domains.product.dto;

import com.locnguyen.ecommerce.domains.returns.enums.ReturnPolicy;

import com.locnguyen.ecommerce.domains.product.enums.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

import java.util.UUID;
@Data
@Schema(description = "Update product request — only provided fields are updated")
public class UpdateProductRequest {

    @Size(max = 255)
    private String name;

    private ReturnPolicy returnPolicy;

    @Size(max = 255)
    private String slug;

    @Size(max = 500)
    private String shortDescription;

    private String description;

    private UUID brandId;

    @Schema(description = "Replace category assignments with this list")
    private List<UUID> categoryIds;

    private ProductStatus status;

    private Boolean featured;
}
