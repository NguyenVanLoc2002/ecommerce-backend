package com.locnguyen.ecommerce.infrastructure.payment.momo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Inbound MoMo IPN (Instant Payment Notification) webhook payload.
 *
 * <p>MoMo sends this as a JSON POST body to the configured {@code ipnUrl}.
 * Numeric fields (amount, transId, resultCode, responseTime) are serialized
 * as JSON numbers by MoMo — Jackson maps them to their appropriate Java types.
 *
 * <p>Only fields required for HMAC-SHA256 verification and business logic are mapped.
 * All other fields are ignored via {@code @JsonIgnoreProperties(ignoreUnknown = true)}.
 *
 * <p>The {@code signature} field is the HMAC-SHA256 digest MoMo computed over
 * the other fields (alphabetical order, no URL-encoding). It is extracted from
 * the body itself — MoMo does not use an HTTP signature header.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MomoIpnRequest {

    private String partnerCode;
    private String orderId;
    private String requestId;
    private Long amount;
    private String orderInfo;
    private String orderType;
    private Long transId;
    private Integer resultCode;
    private String message;
    private String payType;
    private Long responseTime;
    private String extraData;
    private String signature;

    /** Optional; present only for certain payment types. May be null. */
    private String paymentOption;
}
