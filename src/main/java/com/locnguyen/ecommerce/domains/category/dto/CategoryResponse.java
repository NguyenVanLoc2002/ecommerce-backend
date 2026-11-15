package com.locnguyen.ecommerce.domains.category.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.category.enums.CategoryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.io.Serializable;
import java.time.LocalDateTime;

import java.util.UUID;

@Getter
@Builder
@Jacksonized
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Category response")
public class CategoryResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final UUID parentId;
    private final String name;
    private final String slug;
    private final String description;
    private final String imageUrl;
    private final String imageStorageKey;
    private final CategoryStatus status;
    private final Integer sortOrder;
    private final LocalDateTime createdAt;
}
