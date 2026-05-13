package com.locnguyen.ecommerce.domains.payment.repository;

import com.locnguyen.ecommerce.domains.payment.entity.PaymentWebhookLog;
import com.locnguyen.ecommerce.domains.payment.enums.WebhookLogStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentWebhookLogRepository extends JpaRepository<PaymentWebhookLog, UUID> {

    List<PaymentWebhookLog> findByProviderTxnIdOrderByCreatedAtDesc(String providerTxnId);

    List<PaymentWebhookLog> findByOrderCodeOrderByCreatedAtDesc(String orderCode);

    List<PaymentWebhookLog> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);

    boolean existsByProviderTxnIdAndStatus(String providerTxnId, WebhookLogStatus status);
}
