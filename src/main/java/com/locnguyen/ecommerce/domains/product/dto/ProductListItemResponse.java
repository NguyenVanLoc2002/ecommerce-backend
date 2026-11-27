package com.locnguyen.ecommerce.domains.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.product.enums.ProductStatus;
import com.locnguyen.ecommerce.domains.productbadge.dto.ProductBadgeModels.Display;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import java.util.UUID;
/**
 * Lightweight product representation for list views.
 * Contains computed price range and thumbnail — no variant/media details.
 */
@Getter
@Builder(toBuilder = true)
@Jacksonized
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Product list item (no variant details)")
public class ProductListItemResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String name;
    private final String slug;
    private final String shortDescription;
    private final String thumbnailUrl;
    private final BigDecimal minPrice;
    private final BigDecimal maxPrice;
    private final ProductStatus status;
    private final boolean featured;
    private final Display badge;
    private final String brandName;
    private final List<String> categoryNames;
    private final LocalDateTime createdAt;
}
