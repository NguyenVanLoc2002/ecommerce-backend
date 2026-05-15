package com.locnguyen.ecommerce.infrastructure.payment.paypal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/** Response from {@code POST /v1/notifications/verify-webhook-signature}. */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaypalWebhookVerifyResponse {

    @JsonProperty("verification_status")
    private String verificationStatus;
}
