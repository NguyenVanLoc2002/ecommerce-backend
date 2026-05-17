package com.locnguyen.ecommerce.infrastructure.external.ahamove.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record AhamoveRequestOption(
        @JsonProperty("_id")
        String id,
        Integer num,
        @JsonProperty("tier_code")
        String tierCode
) {}
