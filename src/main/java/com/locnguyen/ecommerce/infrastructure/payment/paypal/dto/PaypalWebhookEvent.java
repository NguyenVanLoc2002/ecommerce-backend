package com.locnguyen.ecommerce.infrastructure.payment.paypal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/** Inbound PayPal webhook notification envelope. */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaypalWebhookEvent {

    @JsonProperty("id")
    private String id;

    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("resource")
    private PaypalWebhookResource resource;
}
