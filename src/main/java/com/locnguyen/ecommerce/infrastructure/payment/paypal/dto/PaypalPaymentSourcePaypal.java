package com.locnguyen.ecommerce.infrastructure.payment.paypal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/** PayPal wallet node inside {@code payment_source}. */
@Getter
@Builder
public class PaypalPaymentSourcePaypal {

    @JsonProperty("experience_context")
    private final PaypalExperienceContext experienceContext;
}
