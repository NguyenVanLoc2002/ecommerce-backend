package com.locnguyen.ecommerce.domains.category.dto;

import com.locnguyen.ecommerce.domains.returns.enums.ReturnPolicy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;
@Data
@Schema(description = "Create category request")
public class CreateCategoryRequest {

    private UUID parentId;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    @Schema(example = "Áo nam", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    private ReturnPolicy returnPolicy;

    @NotBlank(message = "Slug is required")
    @Size(max = 255)
    @Schema(example = "ao-nam", requiredMode = Schema.RequiredMode.REQUIRED)
    private String slug;

    @Schema(example = "Các loại áo cho nam")
    private String description;

    @Schema(example = "https://cdn.example.com/categories/ao-nam.jpg")
    private String imageUrl;

    @Size(max = 500)
    private String imageStorageKey;

    @Schema(example = "1")
    private Integer sortOrder;
}
