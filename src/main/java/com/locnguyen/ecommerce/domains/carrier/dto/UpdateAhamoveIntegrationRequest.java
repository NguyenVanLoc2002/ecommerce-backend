package com.locnguyen.ecommerce.domains.carrier.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateAhamoveIntegrationRequest {

    @Size(max = 500)
    private String apiKey;

    @Size(max = 500)
    private String secretKey;

    @Size(max = 500)
    private String webhookSecret;

    @Size(max = 500)
    private String baseUrl;

    private Boolean enabled;

    @Size(max = 50)
    private String phone;

    @Size(max = 200)
    private String brandName;

    @Size(max = 500)
    private String pickupAddress;

    @Size(max = 255)
    private String pickupShortAddress;

    @Size(max = 200)
    private String pickupName;

    @Size(max = 50)
    private String pickupPhone;

    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private BigDecimal pickupLat;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private BigDecimal pickupLng;

    @Size(max = 100)
    private String defaultServiceCode;

    @Size(max = 50)
    private String defaultPaymentMethod;
}
