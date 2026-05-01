package com.locnguyen.ecommerce.domains.category.dto;

import com.locnguyen.ecommerce.domains.category.enums.CategoryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;
@Getter
@Builder
@Schema(description = "Category list filters")
public class CategoryFilter {
        @Schema(description = "Search keyword (matches category name)", example = "áo thun")
        private String name;

        @Schema(description = "Filter by slug (exact match)", example = "ao-thun")
        private String slug;

        @Schema(description = "Filter by parent category ID", example = "1")
        private UUID parentId;

        @Schema(description = "Filter by category status", example = "ACTIVE")
        private CategoryStatus status;

        @Schema(description = "Soft delete filter: false=active only, true=deleted only", example = "false")
        private Boolean isDeleted;

        @Schema(description = "Include both active and deleted rows", example = "false")
        private Boolean includeDeleted;
}
