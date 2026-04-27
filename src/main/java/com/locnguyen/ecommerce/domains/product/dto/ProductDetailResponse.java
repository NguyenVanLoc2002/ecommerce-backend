package com.locnguyen.ecommerce.domains.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.brand.dto.BrandResponse;
import com.locnguyen.ecommerce.domains.category.dto.CategoryResponse;
import com.locnguyen.ecommerce.domains.product.enums.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full product detail — includes brand, categories, variants (with attributes), and media.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Product detail with variants and media")
public class ProductDetailResponse {

    private final Long id;
    private final String name;
    private final String slug;
    private final String shortDescription;
    private final String description;
    private final ProductStatus status;
    private final boolean featured;
    private final BrandResponse brand;
    private final List<CategoryResponse> categories;
    private final List<VariantResponse> variants;
    private final List<MediaResponse> media;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
