package com.locnguyen.ecommerce.infrastructure.payment.paypal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaypalRefundDetails(String id, String status, @JsonProperty("invoice_id") String invoiceId,
                                  Amount amount, List<PaypalLink> links) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Amount(@JsonProperty("currency_code") String currency, BigDecimal value) {}
}
