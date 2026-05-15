package com.locnguyen.ecommerce.infrastructure.payment.paypal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;
import java.util.Optional;

/** Top-level response from {@code POST /v2/checkout/orders/{id}/capture}. */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaypalCaptureOrderResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("status")
    private String status;

    @JsonProperty("purchase_units")
    private List<PaypalCapturePurchaseUnit> purchaseUnits;

    /** Extracts the first capture from the first purchase unit, or empty if not present. */
    public Optional<PaypalCapture> firstCapture() {
        if (purchaseUnits == null || purchaseUnits.isEmpty()) return Optional.empty();
        PaypalCapturePayments payments = purchaseUnits.get(0).getPayments();
        if (payments == null || payments.getCaptures() == null || payments.getCaptures().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(payments.getCaptures().get(0));
    }
}
