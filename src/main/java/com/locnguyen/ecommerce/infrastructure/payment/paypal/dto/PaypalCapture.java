package com.locnguyen.ecommerce.infrastructure.payment.paypal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/** A single capture record within a PayPal order's purchase unit. */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaypalCapture {

    @JsonProperty("id")
    private String id;

    @JsonProperty("status")
    private String status;

    /** Equals the {@code custom_id} set on the purchase unit during order creation (= orderCode). */
    @JsonProperty("custom_id")
    private String customId;

    @JsonProperty("amount")
    private PaypalAmount amount;
}
