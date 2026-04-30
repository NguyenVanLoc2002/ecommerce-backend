package com.locnguyen.ecommerce.domains.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.productvariant.enums.ProductVariantStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Product variant detail")
public class VariantResponse {

    private final UUID id;
    private final UUID productId;
    private final String sku;
    private final String barcode;
    private final String variantName;
    private final BigDecimal basePrice;
    private final BigDecimal salePrice;
    private final BigDecimal compareAtPrice;
    private final Integer weightGram;
    private final ProductVariantStatus status;

    @Schema(description = "Attributes selected for this variant")
    private final List<VariantAttributeResponse> attributes;
}
