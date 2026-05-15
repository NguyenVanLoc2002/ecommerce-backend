package com.locnguyen.ecommerce.infrastructure.payment.paypal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Request body for {@code POST /v2/checkout/orders}. */
@Getter
@Builder
public class PaypalCreateOrderRequest {

    /** Always {@code CAPTURE} for one-time payments. */
    @JsonProperty("intent")
    private final String intent;

    @JsonProperty("purchase_units")
    private final List<PaypalPurchaseUnit> purchaseUnits;

    /**
     * Drives the checkout experience via {@code payment_source.paypal.experience_context}.
     * This is the current recommended approach in the PayPal Orders API v2.
     * The deprecated {@code application_context} field is not used.
     */
    @JsonProperty("payment_source")
    private final PaypalPaymentSource paymentSource;
}
