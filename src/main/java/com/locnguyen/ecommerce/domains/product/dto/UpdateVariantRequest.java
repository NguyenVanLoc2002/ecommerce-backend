package com.locnguyen.ecommerce.domains.product.dto;

import com.locnguyen.ecommerce.domains.productvariant.enums.ProductVariantStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Data
@Schema(description = "Update variant request — only provided fields are changed; "
        + "if attributeValueIds is provided, the attribute set is replaced.")
public class UpdateVariantRequest {

    @Size(max = 100)
    private String sku;

    private Boolean autoGenerateSku;

    @Size(max = 100)
    private String barcode;

    private Boolean autoGenerateBarcode;

    @Size(max = 255)
    private String variantName;

    private Boolean autoGenerateVariantName;

    @DecimalMin(value = "0.0", inclusive = true, message = "Base price must be >= 0")
    private BigDecimal basePrice;

    @DecimalMin(value = "0.0", inclusive = true, message = "Sale price must be >= 0")
    private BigDecimal salePrice;

    @DecimalMin(value = "0.0", inclusive = true, message = "Compare-at price must be >= 0")
    private BigDecimal compareAtPrice;

    private Integer weightGram;

    private ProductVariantStatus status;

    @Schema(description = "Replace attribute values — must all belong to VARIANT-type attributes "
            + "and at most one value per attribute")
    private Set<UUID> attributeValueIds;
}
