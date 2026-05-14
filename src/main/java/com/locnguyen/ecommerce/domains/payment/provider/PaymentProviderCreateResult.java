package com.locnguyen.ecommerce.domains.payment.provider;

import lombok.Builder;
import lombok.Getter;

/**
 * Value object returned by {@link PaymentProvider#createPayment}.
 *
 * <p>Carries the redirect URL and any provider-specific references that
 * need to be stored for later IPN callback mapping.
 */
@Getter
@Builder
public class PaymentProviderCreateResult {

    /** Primary web redirect URL for the customer to complete payment. */
    private final String paymentUrl;

    /** Mobile app deeplink (may be null if provider doesn't support it). */
    private final String deeplink;

    /**
     * QR code data string — raw data to encode into a QR image, NOT an image URL.
     * May be null if provider doesn't return QR data.
     */
    private final String qrCodeUrl;

    /** Provider-assigned order identifier — stored for IPN callback mapping. */
    private final String providerOrderId;

    /** Provider-assigned request identifier — stored for audit/debugging. */
    private final String providerRequestId;

    /** Result code from the provider's create-payment response. */
    private final Integer resultCode;

    /** Human-readable message from the provider's create-payment response. */
    private final String message;
}
