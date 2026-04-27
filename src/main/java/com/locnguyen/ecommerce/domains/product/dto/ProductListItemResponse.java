package com.locnguyen.ecommerce.domains.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.product.enums.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lightweight product representation for list views.
 * Contains computed price range and thumbnail — no variant/media details.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Product list item (no variant details)")
public class ProductListItemResponse {

    private final Long id;
    private final String name;
    private final String slug;
    private final String shortDescription;
    private final String thumbnailUrl;
    private final BigDecimal minPrice;
    private final BigDecimal maxPrice;
    private final ProductStatus status;
    private final boolean featured;
    private final String brandName;
    private final List<String> categoryNames;
    private final LocalDateTime createdAt;
}
