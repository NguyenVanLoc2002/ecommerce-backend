package com.locnguyen.ecommerce.domains.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request body for the capture endpoint — provider-neutral. */
@Getter
@Setter
@NoArgsConstructor
public class PaymentCaptureRequest {

    /** Payment provider name, e.g. {@code PAYPAL}. Case-insensitive. */
    @NotBlank(message = "Provider is required")
    @Size(max = 50)
    private String provider;

    /**
     * Provider-assigned order token returned at initiation and present in the
     * redirect URL (e.g., PayPal's {@code ?token=} query param).
     */
    @NotBlank(message = "Provider token is required")
    @Size(max = 200)
    private String providerToken;
}
