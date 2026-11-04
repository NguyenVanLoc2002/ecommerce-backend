package com.locnguyen.ecommerce.domains.payment.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PaypalPaymentConfigResponse {
    private final String provider;
    private final boolean managedInDatabase;
    private final boolean enabled;
    private final String environment;
    private final boolean hasClientId;
    private final boolean hasClientSecret;
    private final String baseUrl;
    private final String returnUrl;
    private final String cancelUrl;
    private final String webhookId;
    private final String currency;
    private final String brandName;
    private final String locale;
    private final String userAction;
    private final String paymentMethodPreference;
    private final String shippingPreference;
    private final BigDecimal conversionRateVndToUsd;
    private final String conversionRateSource;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
}
