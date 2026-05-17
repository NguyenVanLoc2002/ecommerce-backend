package com.locnguyen.ecommerce.infrastructure.external.ahamove.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record AhamoveItem(
        @JsonProperty("_id")
        String id,
        Integer num,
        String name,
        BigDecimal price
) {}
