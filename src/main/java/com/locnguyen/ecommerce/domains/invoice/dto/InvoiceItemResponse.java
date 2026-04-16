package com.locnguyen.ecommerce.domains.invoice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A single line item in the invoice")
public class InvoiceItemResponse {

    private final Long variantId;
    private final String productName;
    private final String variantName;
    private final String sku;
    private final BigDecimal unitPrice;

    /** Sale price at time of purchase — null if full price was paid. */
    private final BigDecimal salePrice;

    /** Effective price per unit (salePrice if present, otherwise unitPrice). */
    private final BigDecimal effectivePrice;

    private final Integer quantity;
    private final BigDecimal lineTotal;
}
