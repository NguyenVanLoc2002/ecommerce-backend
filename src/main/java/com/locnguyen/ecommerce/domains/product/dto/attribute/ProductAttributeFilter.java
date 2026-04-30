package com.locnguyen.ecommerce.domains.product.dto.attribute;

import com.locnguyen.ecommerce.domains.product.enums.AttributeType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Product attribute list filter")
public class ProductAttributeFilter {

    @Schema(description = "Filter by attribute type", example = "VARIANT")
    private AttributeType type;

    @Schema(description = "Partial, case-insensitive match on name or code", example = "color")
    private String keyword;
}
