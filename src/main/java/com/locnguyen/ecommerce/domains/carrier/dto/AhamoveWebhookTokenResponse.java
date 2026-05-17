package com.locnguyen.ecommerce.domains.carrier.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AhamoveWebhookTokenResponse {
    private final String token;
    private final String maskedToken;
    private final LocalDateTime generatedAt;
}
