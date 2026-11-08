package com.locnguyen.ecommerce.domains.cart.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.UUID;
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Cart item response")
public class CartItemResponse {

    private final UUID id;
    private final UUID variantId;
    private final String variantName;
    private final String sku;
    private final UUID productId;
    private final String productSlug;
    private final String productName;
    private final String brandName;
    private final String productImageUrl;
    private final BigDecimal unitPrice;
    private final BigDecimal salePrice;
    private final Integer quantity;
    private final int availableStock;
    private final BigDecimal lineTotal;
    private final LocalDateTime createdAt;
}
