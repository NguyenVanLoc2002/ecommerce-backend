package com.locnguyen.ecommerce.domains.category.dto;

import com.locnguyen.ecommerce.domains.returns.enums.ReturnPolicy;

import com.locnguyen.ecommerce.domains.category.enums.CategoryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;
@Data
@Schema(description = "Update category request — only provided fields are updated")
public class UpdateCategoryRequest {

    private UUID parentId;

    @Size(max = 100)
    private String name;

    private ReturnPolicy returnPolicy;

    @Size(max = 255)
    private String slug;

    private String description;

    private String imageUrl;

    @Size(max = 500)
    private String imageStorageKey;

    private CategoryStatus status;

    private Integer sortOrder;
}
