package com.locnguyen.ecommerce.infrastructure.payment.paypal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/** The {@code resource} object inside a PayPal webhook event. Capture and order events share this shape. */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaypalWebhookResource {

    /** Capture ID for PAYMENT.CAPTURE.* events — used as providerTxnId. */
    @JsonProperty("id")
    private String id;

    @JsonProperty("status")
    private String status;

    /** Equals the purchase unit's {@code custom_id} set during order creation (= orderCode). */
    @JsonProperty("custom_id")
    private String customId;

    @JsonProperty("amount")
    private PaypalAmount amount;
}
