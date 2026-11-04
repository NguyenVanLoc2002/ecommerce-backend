package com.locnguyen.ecommerce.domains.payment.config;

import java.math.BigDecimal;

public record PaypalResolvedPaymentConfig(
        boolean enabled,
        String environment,
        String clientId,
        String clientSecret,
        String baseUrl,
        String returnUrl,
        String cancelUrl,
        String webhookId,
        String currency,
        String brandName,
        String locale,
        String userAction,
        String paymentMethodPreference,
        String shippingPreference,
        BigDecimal conversionRateVndToUsd,
        int connectTimeoutMs,
        int readTimeoutMs
) {
}
