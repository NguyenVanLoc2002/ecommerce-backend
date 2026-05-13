package com.locnguyen.ecommerce.domains.payment.service;

import com.locnguyen.ecommerce.domains.payment.dto.RefundRequest;
import com.locnguyen.ecommerce.domains.payment.dto.RefundResponse;

import java.util.List;
import java.util.UUID;

public interface PaymentRefundService {

    RefundResponse initiateRefund(UUID paymentId, RefundRequest request);

    List<RefundResponse> getRefundsForPayment(UUID paymentId);

    RefundResponse getRefundByCode(String refundCode);
}
