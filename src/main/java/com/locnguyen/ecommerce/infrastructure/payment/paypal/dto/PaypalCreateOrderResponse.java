package com.locnguyen.ecommerce.infrastructure.payment.paypal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

import java.util.List;
import java.util.Optional;

/** Response from {@code POST /v2/checkout/orders}. */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaypalCreateOrderResponse {

    /** PayPal-assigned order ID, e.g. {@code 5O190127TN364715T}. */
    private String id;

    /**
     * Order status, e.g. {@code PAYER_ACTION_REQUIRED} after creation with
     * {@code payment_source.paypal}.
     */
    private String status;

    private List<PaypalLink> links;

    /**
     * Finds the customer approval URL from the HATEOAS links array.
     *
     * <p>PayPal uses {@code rel="payer-action"} when the order was created with
     * {@code payment_source.paypal.experience_context} (current recommended API).
     * The legacy {@code rel="approve"} is also checked for compatibility.
     */
    public Optional<String> findApprovalUrl() {
        if (links == null) return Optional.empty();
        return links.stream()
                .filter(l -> "payer-action".equals(l.getRel()) || "approve".equals(l.getRel()))
                .findFirst()
                .map(PaypalLink::getHref);
    }
}
