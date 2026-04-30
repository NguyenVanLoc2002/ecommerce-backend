package com.locnguyen.ecommerce.domains.product.dto.attribute;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.product.enums.AttributeType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Reusable product attribute with its values")
public class ProductAttributeResponse {

    private final UUID id;
    private final String name;
    private final String code;
    private final AttributeType type;
    private final List<ProductAttributeValueResponse> values;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
