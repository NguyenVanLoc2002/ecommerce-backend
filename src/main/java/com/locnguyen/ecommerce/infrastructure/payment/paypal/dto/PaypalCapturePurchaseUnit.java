package com.locnguyen.ecommerce.infrastructure.payment.paypal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/** A purchase unit in the PayPal capture-order response. */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaypalCapturePurchaseUnit {

    @JsonProperty("reference_id")
    private String referenceId;

    @JsonProperty("payments")
    private PaypalCapturePayments payments;
}
