package com.locnguyen.ecommerce.domains.product.dto;

import com.locnguyen.ecommerce.domains.returns.enums.ReturnPolicy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.brand.dto.BrandResponse;
import com.locnguyen.ecommerce.domains.category.dto.CategoryResponse;
import com.locnguyen.ecommerce.domains.product.enums.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import com.locnguyen.ecommerce.domains.sizeguide.dto.SizeGuideModels.Display;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.locnguyen.ecommerce.domains.productbadge.dto.ProductBadgeModels;
import java.util.List;

import java.util.UUID;
/**
 * Full product detail — includes brand, categories, variants (with attributes), and media.
 */
@Getter
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Product detail with variants and media")
public class ProductDetailResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String name;
    private final ReturnPolicy returnPolicy;
    private final Display sizeGuide;
    private final List<ProductBadgeModels.Display> badges;
    private final String slug;
    private final String shortDescription;
    private final String description;
    private final BigDecimal minPrice;
    private final BigDecimal maxPrice;
    private final ProductStatus status;
    private final boolean featured;
    private final BrandResponse brand;
    private final List<CategoryResponse> categories;
    private final List<VariantResponse> variants;
    private final List<MediaResponse> media;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
