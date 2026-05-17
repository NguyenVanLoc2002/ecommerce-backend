package com.locnguyen.ecommerce.domains.carrier.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierConnectionStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AhamoveIntegrationResponse {
    private final UUID carrierId;
    private final String carrierCode;
    private final String carrierName;
    private final Boolean enabled;
    private final String baseUrl;
    private final Boolean hasApiKey;
    private final Boolean hasSecretKey;
    private final Boolean hasWebhookSecret;
    private final String phone;
    private final String brandName;
    private final String pickupAddress;
    private final String pickupShortAddress;
    private final String pickupName;
    private final String pickupPhone;
    private final BigDecimal pickupLat;
    private final BigDecimal pickupLng;
    private final String defaultServiceCode;
    private final String defaultPaymentMethod;
    private final CarrierConnectionStatus connectionStatus;
    private final LocalDateTime lastHealthCheckAt;
    private final String lastHealthCheckError;
    private final String maskedWebhookToken;
}
