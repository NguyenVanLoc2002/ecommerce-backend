package com.locnguyen.ecommerce.domains.payment.repository;

import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long>,
        JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByPaymentCode(String paymentCode);

    boolean existsByOrderId(Long orderId);
}
