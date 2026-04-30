package com.locnguyen.ecommerce.domains.product.dto.attribute;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A single value under a product attribute")
public class ProductAttributeValueResponse {

    private final UUID id;
    private final String value;
    private final String displayValue;
}
