package com.locnguyen.ecommerce.domains.payment.provider.momo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * HTTP request body sent to MoMo {@code POST /v2/gateway/api/create}.
 *
 * <p>Field order in JSON is not significant; the HMAC signature is computed
 * over a fixed alphabetically-ordered raw string — see {@code MomoSignatureService}.
 */
@Getter
@Builder
public class MomoCreatePaymentRequest {

    @JsonProperty("partnerCode")
    private final String partnerCode;

    @JsonProperty("accessKey")
    private final String accessKey;

    @JsonProperty("requestType")
    private final String requestType;

    @JsonProperty("ipnUrl")
    private final String ipnUrl;

    @JsonProperty("redirectUrl")
    private final String redirectUrl;

    @JsonProperty("orderId")
    private final String orderId;

    @JsonProperty("amount")
    private final Long amount;

    @JsonProperty("orderInfo")
    private final String orderInfo;

    @JsonProperty("requestId")
    private final String requestId;

    @JsonProperty("extraData")
    private final String extraData;

    @JsonProperty("signature")
    private final String signature;

    @JsonProperty("lang")
    private final String lang;
}
