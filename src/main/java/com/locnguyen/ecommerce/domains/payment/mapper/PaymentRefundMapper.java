package com.locnguyen.ecommerce.domains.payment.mapper;

import com.locnguyen.ecommerce.domains.payment.dto.RefundResponse;
import com.locnguyen.ecommerce.domains.payment.entity.PaymentRefund;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PaymentRefundMapper {

    default RefundResponse toResponse(PaymentRefund refund) {
        if (refund == null) return null;

        return new RefundResponse(
                refund.getId(),
                refund.getPayment().getId(),
                refund.getRefundCode(),
                refund.getAmount(),
                refund.getReason(),
                refund.getStatus(),
                refund.getProviderRefundId(),
                refund.getRefundedAt(),
                refund.getRequestedBy(),
                refund.getNote(),
                refund.getCreatedAt()
        );
    }

    default List<RefundResponse> toResponses(List<PaymentRefund> refunds) {
        if (refunds == null) return List.of();
        return refunds.stream().map(this::toResponse).toList();
    }
}
