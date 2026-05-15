package com.locnguyen.ecommerce.infrastructure.payment.paypal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/** Request body for {@code POST /v1/notifications/verify-webhook-signature}. */
@Getter
@Builder
public class PaypalWebhookVerifyRequest {

    @JsonProperty("auth_algo")
    private final String authAlgo;

    @JsonProperty("cert_url")
    private final String certUrl;

    @JsonProperty("transmission_id")
    private final String transmissionId;

    @JsonProperty("transmission_sig")
    private final String transmissionSig;

    @JsonProperty("transmission_time")
    private final String transmissionTime;

    @JsonProperty("webhook_id")
    private final String webhookId;

    /** The parsed webhook event body — serialized as-is into the verify request. */
    @JsonProperty("webhook_event")
    private final Object webhookEvent;
}
