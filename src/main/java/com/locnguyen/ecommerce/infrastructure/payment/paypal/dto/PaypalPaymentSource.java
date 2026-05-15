package com.locnguyen.ecommerce.infrastructure.payment.paypal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/** {@code payment_source} root for PayPal Orders API v2. */
@Getter
@Builder
public class PaypalPaymentSource {

    @JsonProperty("paypal")
    private final PaypalPaymentSourcePaypal paypal;
}
