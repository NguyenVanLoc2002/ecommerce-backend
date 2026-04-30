package com.locnguyen.ecommerce.domains.product.dto.attribute;

import com.locnguyen.ecommerce.domains.product.enums.AttributeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Partial update for a product attribute. Only provided fields are changed; "
        + "if 'values' is provided, the value set is replaced.")
public class UpdateProductAttributeRequest {

    @Size(max = 100)
    private String name;

    @Size(max = 50)
    @Schema(description = "If provided, normalized to upper snake-case server-side")
    private String code;

    private AttributeType type;

    @Valid
    @Schema(description = "Replacement value list. Existing values not present here are removed "
            + "(unless still in use by variants — the request is rejected then).")
    private List<CreateProductAttributeValueRequest> values;
}
