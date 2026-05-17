package com.locnguyen.ecommerce.domains.carrier.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AhamoveWebhookSetupResponse {
    private final String webhookUrl;
    private final String authHeader;
    private final String authScheme;
    private final boolean hasWebhookToken;
    private final String maskedWebhookToken;
    private final List<String> instructions;
}
