package com.locnguyen.ecommerce.domains.product.dto.attribute;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Create or update an attribute value")
public class CreateProductAttributeValueRequest {

    @NotBlank(message = "Value is required")
    @Size(max = 100)
    @Schema(example = "WHITE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String value;

    @Size(max = 100)
    @Schema(example = "Trắng")
    private String displayValue;
}
