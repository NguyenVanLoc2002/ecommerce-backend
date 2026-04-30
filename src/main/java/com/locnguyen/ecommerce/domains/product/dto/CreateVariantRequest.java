package com.locnguyen.ecommerce.domains.product.dto;

import com.locnguyen.ecommerce.domains.productvariant.enums.ProductVariantStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@Schema(description = "Create product variant request — attributes are referenced by id")
public class CreateVariantRequest {

    @Size(max = 100)
    @Schema(example = "ATBN-WH-M",
            description = "Optional. If blank or autoGenerateSku=true the server generates one.")
    private String sku;

    @Schema(description = "When true (or sku blank) the server generates the SKU.",
            example = "false")
    private Boolean autoGenerateSku;

    @Size(max = 100)
    @Schema(example = "9876543210", description = "Optional barcode — uniqueness enforced when present")
    private String barcode;

    @Schema(description = "When true (or barcode blank) the server generates the barcode.",
            example = "false")
    private Boolean autoGenerateBarcode;

    @Size(max = 255)
    @Schema(example = "Trắng / M",
            description = "Optional. If blank or autoGenerateVariantName=true the server builds it from the selected attribute display values.")
    private String variantName;

    @Schema(description = "When true (or variantName blank) the server generates the variant name.",
            example = "true")
    private Boolean autoGenerateVariantName;

    @NotNull(message = "Base price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Base price must be >= 0")
    @Schema(example = "200000", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal basePrice;

    @DecimalMin(value = "0.0", inclusive = true, message = "Sale price must be >= 0")
    @Schema(example = "150000", description = "Must be <= basePrice when present")
    private BigDecimal salePrice;

    @DecimalMin(value = "0.0", inclusive = true, message = "Compare-at price must be >= 0")
    @Schema(example = "250000", description = "Must be >= basePrice when present")
    private BigDecimal compareAtPrice;

    @Schema(example = "200", description = "Optional. Must be > 0 when present.")
    private Integer weightGram;

    private ProductVariantStatus status;

    @Schema(description = "ProductAttributeValue ids — must all belong to VARIANT-type attributes "
            + "and at most one value per attribute")
    private Set<UUID> attributeValueIds = new HashSet<>();
}
