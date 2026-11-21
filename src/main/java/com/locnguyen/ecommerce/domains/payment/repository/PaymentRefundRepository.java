package com.locnguyen.ecommerce.domains.payment.repository;

import com.locnguyen.ecommerce.domains.payment.entity.PaymentRefund;
import com.locnguyen.ecommerce.domains.payment.enums.RefundStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {

    List<PaymentRefund> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PaymentRefund> findByPaymentIdAndStatusIn(UUID paymentId, List<RefundStatus> statuses);

    Optional<PaymentRefund> findByRefundCode(String refundCode);

    boolean existsByPaymentIdAndStatus(UUID paymentId, RefundStatus status);
}
