package com.locnguyen.ecommerce.domains.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

import java.util.UUID;
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Order item response — snapshot of product data at checkout")
public class OrderItemResponse {

    private final UUID id;
    private final UUID variantId;
    private final String productName;
    private final String variantName;
    private final String sku;
    private final String productImageUrl;
    private final BigDecimal unitPrice;
    private final BigDecimal salePrice;
    private final Integer quantity;
    private final BigDecimal lineTotal;
}
