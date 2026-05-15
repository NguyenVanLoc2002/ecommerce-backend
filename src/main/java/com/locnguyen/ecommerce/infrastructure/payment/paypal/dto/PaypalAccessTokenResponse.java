package com.locnguyen.ecommerce.infrastructure.payment.paypal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/** Response from {@code POST /v1/oauth2/token}. */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaypalAccessTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    /** Token lifetime in seconds. */
    @JsonProperty("expires_in")
    private int expiresIn;
}
