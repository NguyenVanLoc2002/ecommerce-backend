package com.locnguyen.ecommerce.domains.category.dto;

import com.locnguyen.ecommerce.domains.category.enums.CategoryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Update category request — only provided fields are updated")
public class UpdateCategoryRequest {

    private Long parentId;

    @Size(max = 100)
    private String name;

    @Size(max = 255)
    private String slug;

    private String description;

    private String imageUrl;

    private CategoryStatus status;

    private Integer sortOrder;
}
