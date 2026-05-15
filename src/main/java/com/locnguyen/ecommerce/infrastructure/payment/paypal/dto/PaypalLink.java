package com.locnguyen.ecommerce.infrastructure.payment.paypal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

/** HATEOAS link object returned in PayPal API responses. */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaypalLink {

    private String href;
    private String rel;
    private String method;
}
