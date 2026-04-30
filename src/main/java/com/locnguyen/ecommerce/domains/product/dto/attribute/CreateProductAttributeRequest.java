package com.locnguyen.ecommerce.domains.product.dto.attribute;

import com.locnguyen.ecommerce.domains.product.enums.AttributeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Create a reusable product attribute and (optionally) seed its values")
public class CreateProductAttributeRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100)
    @Schema(example = "Color", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "Code is required")
    @Size(max = 50)
    @Schema(example = "COLOR", requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Stable identifier — normalized to upper snake-case server-side")
    private String code;

    @NotNull(message = "Type is required")
    @Schema(example = "VARIANT", requiredMode = Schema.RequiredMode.REQUIRED)
    private AttributeType type;

    @Valid
    @Schema(description = "Initial values to seed under this attribute")
    private List<CreateProductAttributeValueRequest> values;
}
