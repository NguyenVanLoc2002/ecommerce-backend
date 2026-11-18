package com.locnguyen.ecommerce.domains.payment.repository;

import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import com.locnguyen.ecommerce.domains.payment.enums.PaymentRecordStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID>,
        JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByPaymentCode(String paymentCode);

    Optional<Payment> findByProviderOrderId(String providerOrderId);

    boolean existsByOrderId(UUID orderId);

    /**
     * Load payment by order with a PESSIMISTIC_WRITE (SELECT ... FOR UPDATE) lock.
     *
     * <p>Used by callback, capture, and COD collection flows after acquiring the
     * corresponding Order lock. Keeping the lock order as Order then Payment
     * serializes payment mutations with fulfillment and cancellation transitions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId")
    Optional<Payment> findByOrderIdWithLock(@Param("orderId") UUID orderId);

    /**
     * Find online payments that have passed their expiry deadline and are still
     * in an INITIATED or PENDING state. Used by the scheduler to auto-cancel
     * orders whose payment window has elapsed.
     */
    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.expiredAt < :now")
    List<Payment> findExpiredByStatus(
            @Param("status") PaymentRecordStatus status,
            @Param("now") LocalDateTime now);
}
