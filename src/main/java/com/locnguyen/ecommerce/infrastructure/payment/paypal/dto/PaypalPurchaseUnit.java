package com.locnguyen.ecommerce.infrastructure.payment.paypal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/** Purchase unit for PayPal Orders API v2. */
@Getter
@Builder
public class PaypalPurchaseUnit {

    @JsonProperty("reference_id")
    private final String referenceId;

    /** Propagated to capture objects in webhooks as {@code resource.custom_id}. Set to orderCode. */
    @JsonProperty("custom_id")
    private final String customId;

    @JsonProperty("amount")
    private final PaypalAmount amount;
}
