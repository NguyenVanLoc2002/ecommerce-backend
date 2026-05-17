package com.locnguyen.ecommerce.domains.carrier.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierConnectionStatus;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CarrierResponse {
    private final UUID id;
    private final String code;
    private final String name;
    private final CarrierProviderType providerType;
    private final CarrierStatus status;
    private final String logoUrl;
    private final String description;
    private final Boolean configEnabled;
    private final String baseUrl;
    private final Boolean hasApiKey;
    private final Boolean hasSecretKey;
    private final Boolean hasWebhookSecret;
    private final String configJson;
    private final CarrierConnectionStatus connectionStatus;
    private final LocalDateTime lastHealthCheckAt;
    private final String lastHealthCheckError;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
