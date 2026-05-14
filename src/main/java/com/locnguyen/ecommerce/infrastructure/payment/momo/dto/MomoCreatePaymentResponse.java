package com.locnguyen.ecommerce.infrastructure.payment.momo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * HTTP response body received from MoMo {@code POST /v2/gateway/api/create}.
 *
 * <p>{@code resultCode == 0} means MoMo successfully created the payment request.
 * It does NOT mean the customer has paid. Payment confirmation arrives via IPN.
 *
 * <p>{@code qrCodeUrl} is raw QR code data — not an image URL.
 * Encode it as a QR image on the client side.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MomoCreatePaymentResponse {

    @JsonProperty("partnerCode")
    private String partnerCode;

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("amount")
    private Long amount;

    @JsonProperty("responseTime")
    private Long responseTime;

    @JsonProperty("message")
    private String message;

    @JsonProperty("resultCode")
    private Integer resultCode;

    /** Web URL to redirect the customer to for completing payment. */
    @JsonProperty("payUrl")
    private String payUrl;

    /** Deeplink to open the MoMo mobile app directly. */
    @JsonProperty("deeplink")
    private String deeplink;

    /**
     * Raw QR code data string — NOT an image URL.
     * Clients must encode this as a QR image (e.g. via a QR library).
     */
    @JsonProperty("qrCodeUrl")
    private String qrCodeUrl;

    /** Deeplink for MoMo mini-app (may be absent). */
    @JsonProperty("deeplinkMiniApp")
    private String deeplinkMiniApp;

    /** Signature returned by MoMo for response verification. */
    @JsonProperty("signature")
    private String signature;
}
