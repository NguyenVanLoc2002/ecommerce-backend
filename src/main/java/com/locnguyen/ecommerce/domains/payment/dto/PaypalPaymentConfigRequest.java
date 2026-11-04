package com.locnguyen.ecommerce.domains.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PaypalPaymentConfigRequest {

    private Boolean enabled;
    private String environment;
    private String clientId;
    private String clientSecret;
    private String baseUrl;
    private String returnUrl;
    private String cancelUrl;
    private String webhookId;
    private String currency;
    private String brandName;
    private String locale;
    private String userAction;
    private String paymentMethodPreference;
    private String shippingPreference;
    @DecimalMin(value = "0.000001", inclusive = true, message = "conversionRateVndToUsd must be greater than 0")
    private BigDecimal conversionRateVndToUsd;

    @Min(value = 1, message = "connectTimeoutMs must be greater than 0")
    private Integer connectTimeoutMs;

    @Min(value = 1, message = "readTimeoutMs must be greater than 0")
    private Integer readTimeoutMs;
}
