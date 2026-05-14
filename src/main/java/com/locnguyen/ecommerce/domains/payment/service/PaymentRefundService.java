package com.locnguyen.ecommerce.domains.payment.service;

import com.locnguyen.ecommerce.domains.payment.dto.RefundRequest;
import com.locnguyen.ecommerce.domains.payment.dto.RefundResponse;

import java.util.List;
import java.util.UUID;

public interface PaymentRefundService {

    RefundResponse initiateRefund(UUID paymentId, RefundRequest request);

    /**
     * Mark a PENDING refund as COMPLETED after the provider confirms the funds transfer.
     *
     * <p>Idempotent: if the refund is already COMPLETED, returns it silently.
     * Rejects any non-PENDING, non-COMPLETED status with {@code PAYMENT_REFUND_INVALID_STATUS}.
     *
     * @param refundCode      the refund's unique business code
     * @param providerRefundId the provider's own refund transaction reference (may be null)
     * @return updated refund record
     */
    RefundResponse completeRefund(String refundCode, String providerRefundId);

    List<RefundResponse> getRefundsForPayment(UUID paymentId);

    RefundResponse getRefundByCode(String refundCode);
}
