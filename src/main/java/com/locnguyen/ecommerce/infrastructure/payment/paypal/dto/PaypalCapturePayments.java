package com.locnguyen.ecommerce.infrastructure.payment.paypal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/** The {@code payments} object nested inside a purchase unit in a capture response. */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaypalCapturePayments {

    @JsonProperty("captures")
    private List<PaypalCapture> captures;
}
