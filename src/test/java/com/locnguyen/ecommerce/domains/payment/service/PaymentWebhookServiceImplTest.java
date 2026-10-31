package com.locnguyen.ecommerce.domains.payment.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.payment.entity.PaymentWebhookLog;
import com.locnguyen.ecommerce.domains.payment.mapper.PaymentMapper;
import com.locnguyen.ecommerce.domains.payment.mapper.PaymentWebhookLogMapper;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProvider;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProviderRegistry;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentRepository;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentTransactionRepository;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentWebhookLogRepository;
import com.locnguyen.ecommerce.domains.payment.service.impl.PaymentWebhookLogPersister;
import com.locnguyen.ecommerce.domains.payment.service.impl.PaymentWebhookServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceImplTest {

    @Mock PaymentWebhookLogRepository webhookLogRepository;
    @Mock PaymentWebhookLogMapper webhookLogMapper;
    @Mock PaymentWebhookLogPersister webhookLogPersister;
    @Mock PaymentProviderRegistry providerRegistry;
    @Mock OrderRepository orderRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock PaymentTransactionRepository transactionRepository;
    @Mock PaymentMapper paymentMapper;
    @Mock PaymentProvider paymentProvider;

    @InjectMocks PaymentWebhookServiceImpl paymentWebhookService;

    @Test
    void receiveWebhook_whenSignatureIsInvalid_rejectsBeforePaymentMutation() {
        PaymentWebhookLog webhookLog = new PaymentWebhookLog();
        when(webhookLogPersister.createInitialLog("MOMO", "spoofed-body", "spoofed-signature"))
                .thenReturn(webhookLog);
        when(providerRegistry.find("MOMO")).thenReturn(Optional.of(paymentProvider));
        when(paymentProvider.verifySignature("spoofed-body", "spoofed-signature")).thenReturn(false);

        assertThatThrownBy(() -> paymentWebhookService.receiveWebhook(
                "MOMO", "spoofed-body", "spoofed-signature"))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID);

        verify(orderRepository, never()).findByOrderCode(org.mockito.ArgumentMatchers.anyString());
        verify(paymentRepository, never()).findByOrderIdWithLock(org.mockito.ArgumentMatchers.any());
        verify(paymentRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(orderRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(transactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
