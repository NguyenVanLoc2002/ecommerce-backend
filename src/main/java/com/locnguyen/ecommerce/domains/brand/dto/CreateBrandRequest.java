package com.locnguyen.ecommerce.domains.brand.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Create brand request")
public class CreateBrandRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100)
    @Schema(example = "Local Brand A", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "Slug is required")
    @Size(max = 255)
    @Schema(example = "local-brand-a", requiredMode = Schema.RequiredMode.REQUIRED)
    private String slug;

    @Schema(example = "https://cdn.example.com/brands/local-brand-a.png")
    private String logoUrl;

    @Size(max = 500)
    private String logoStorageKey;

    private String description;
}
