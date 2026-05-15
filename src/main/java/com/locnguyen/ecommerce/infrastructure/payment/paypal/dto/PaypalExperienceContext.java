package com.locnguyen.ecommerce.infrastructure.payment.paypal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/** Checkout experience context for {@code payment_source.paypal.experience_context}. */
@Getter
@Builder
public class PaypalExperienceContext {

    @JsonProperty("brand_name")
    private final String brandName;

    @JsonProperty("locale")
    private final String locale;

    /** {@code PAY_NOW} shows the payment amount on the PayPal checkout page. */
    @JsonProperty("user_action")
    private final String userAction;

    /** {@code IMMEDIATE_PAYMENT_REQUIRED} — require payment at checkout time. */
    @JsonProperty("payment_method_preference")
    private final String paymentMethodPreference;

    /** {@code NO_SHIPPING} — don't show a shipping address on the PayPal page. */
    @JsonProperty("shipping_preference")
    private final String shippingPreference;

    @JsonProperty("return_url")
    private final String returnUrl;

    @JsonProperty("cancel_url")
    private final String cancelUrl;
}
