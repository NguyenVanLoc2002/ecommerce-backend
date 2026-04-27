package com.locnguyen.ecommerce.domains.payment.mapper;

import com.locnguyen.ecommerce.domains.payment.dto.PaymentResponse;
import com.locnguyen.ecommerce.domains.payment.dto.TransactionResponse;
import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import com.locnguyen.ecommerce.domains.payment.entity.PaymentTransaction;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    /**
     * Full response — includes the transaction audit trail.
     * Use for single-item GET endpoints (within an active transaction).
     */
    default PaymentResponse toResponse(Payment payment) {
        if (payment == null) return null;

        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrder().getId())
                .orderCode(payment.getOrder().getOrderCode())
                .paymentCode(payment.getPaymentCode())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .transactions(toTransactionResponses(payment.getTransactions()))
                .build();
    }

    /**
     * Lightweight response — excludes transactions.
     * Use for paginated list views to avoid N+1 on the transaction collection.
     */
    default PaymentResponse toListItemResponse(Payment payment) {
        if (payment == null) return null;

        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrder().getId())
                .orderCode(payment.getOrder().getOrderCode())
                .paymentCode(payment.getPaymentCode())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    TransactionResponse toTransactionResponse(PaymentTransaction transaction);

    List<TransactionResponse> toTransactionResponses(List<PaymentTransaction> transactions);
}
