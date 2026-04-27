package com.locnguyen.ecommerce.domains.category.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.category.enums.CategoryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Category response")
public class CategoryResponse {

    private final Long id;
    private final Long parentId;
    private final String name;
    private final String slug;
    private final String description;
    private final String imageUrl;
    private final CategoryStatus status;
    private final Integer sortOrder;
    private final LocalDateTime createdAt;
}
