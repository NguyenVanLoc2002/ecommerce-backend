package com.locnguyen.ecommerce.domains.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Variant-side attribute view: which attribute this is, plus the specific value picked.
 * Replaces the old free-form {@code {name, value}} pair.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "An attribute and the value selected for a variant")
public class VariantAttributeResponse {

    private final UUID attributeId;
    private final String attributeName;
    private final String attributeCode;
    private final UUID valueId;
    private final String value;
    private final String displayValue;
}
