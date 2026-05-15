package com.locnguyen.ecommerce.infrastructure.payment.paypal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/** Amount object for PayPal Orders API v2. */
@Getter
@Builder
public class PaypalAmount {

    @JsonProperty("currency_code")
    private final String currencyCode;

    /** Decimal string with 2 fraction digits for USD, e.g. {@code "10.00"}. */
    @JsonProperty("value")
    private final String value;
}
