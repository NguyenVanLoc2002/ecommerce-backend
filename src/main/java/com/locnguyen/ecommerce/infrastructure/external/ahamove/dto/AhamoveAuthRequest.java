package com.locnguyen.ecommerce.infrastructure.external.ahamove.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record AhamoveAuthRequest(
        String mobile,
        @JsonProperty("api_key")
        String apiKey
) {}
