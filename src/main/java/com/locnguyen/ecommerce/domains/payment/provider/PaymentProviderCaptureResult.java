package com.locnguyen.ecommerce.domains.payment.provider;

import lombok.Builder;
import lombok.Getter;

/**
 * Value object returned by {@link PaymentProvider#capturePayment}.
 */
@Getter
@Builder
public class PaymentProviderCaptureResult {

    /** Whether the capture was successful. */
    private final boolean success;

    /** Provider-assigned capture/transaction ID — stored as providerTxnId in audit trail. */
    private final String providerTxnId;

    /** Raw status string from the provider (e.g., "COMPLETED", "DECLINED"). */
    private final String status;

    /** Human-readable message from the provider's capture response. */
    private final String message;
}
