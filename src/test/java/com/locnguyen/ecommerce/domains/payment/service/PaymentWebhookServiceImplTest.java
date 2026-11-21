package com.locnguyen.ecommerce.domains.payment.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import com.locnguyen.ecommerce.domains.payment.entity.PaymentTransaction;
import com.locnguyen.ecommerce.domains.payment.entity.PaymentWebhookLog;
import com.locnguyen.ecommerce.domains.payment.enums.PaymentRecordStatus;
import com.locnguyen.ecommerce.domains.payment.mapper.PaymentMapper;
import com.locnguyen.ecommerce.domains.payment.mapper.PaymentWebhookLogMapper;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProvider;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProviderRegistry;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentRepository;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentTransactionRepository;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentWebhookLogRepository;
import com.locnguyen.ecommerce.domains.payment.service.impl.PaymentWebhookLogPersister;
import com.locnguyen.ecommerce.domains.payment.service.impl.PaymentWebhookServiceImpl;
import com.locnguyen.ecommerce.domains.invoice.service.InvoiceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.math.BigDecimal;
import java.util.UUID;

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
    @Mock InvoiceService invoiceService;
    @Mock PaymentProvider paymentProvider;

    @InjectMocks PaymentWebhookServiceImpl paymentWebhookService;

    private Order cancelledOnlineOrder(UUID id) {
        Order order = new Order();
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", id);
        order.setOrderCode("ORD-LATE");
        order.setPaymentMethod(PaymentMethod.ONLINE);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setStatus(OrderStatus.CANCELLED);
        return order;
    }

    private Payment initiatedPayment(Order order) {
        Payment payment = new Payment();
        org.springframework.test.util.ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        payment.setOrder(order);
        payment.setPaymentCode("PAY-LATE");
        payment.setMethod(PaymentMethod.ONLINE);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setStatus(PaymentRecordStatus.INITIATED);
        return payment;
    }

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

    @Test
    void receiveWebhook_whenAmountDoesNotMatch_rejectsBeforePaymentMutation() {
        PaymentWebhookLog webhookLog = new PaymentWebhookLog();
        Order order = cancelledOnlineOrder(UUID.randomUUID());
        Payment payment = initiatedPayment(order);
        when(webhookLogPersister.createInitialLog("MOMO", "body", "signature"))
                .thenReturn(webhookLog);
        when(providerRegistry.find("MOMO")).thenReturn(Optional.of(paymentProvider));
        when(paymentProvider.verifySignature("body", "signature")).thenReturn(true);
        when(paymentProvider.extractOrderCode("body")).thenReturn(order.getOrderCode());
        when(paymentProvider.extractProviderTxnId("body")).thenReturn("TXN-MISMATCH");
        when(paymentProvider.extractAmount("body")).thenReturn(new BigDecimal("99.00"));
        when(orderRepository.findByOrderCodeWithLock(order.getOrderCode())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdWithLock(order.getId())).thenReturn(Optional.of(payment));
        when(transactionRepository.findByProviderTxnId("TXN-MISMATCH")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentWebhookService.receiveWebhook("MOMO", "body", "signature"))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_CALLBACK_INVALID);

        verify(paymentRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(orderRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(transactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void receiveWebhook_whenProviderTransactionWasProcessed_ignoresDuplicate() {
        PaymentWebhookLog webhookLog = new PaymentWebhookLog();
        Payment payment = new Payment();
        PaymentTransaction existingTransaction = new PaymentTransaction();
        existingTransaction.setPayment(payment);
        when(webhookLogPersister.createInitialLog("MOMO", "body", "signature"))
                .thenReturn(webhookLog);
        when(providerRegistry.find("MOMO")).thenReturn(Optional.of(paymentProvider));
        when(paymentProvider.verifySignature("body", "signature")).thenReturn(true);
        when(paymentProvider.extractProviderTxnId("body")).thenReturn("TXN-DUPLICATE");
        when(transactionRepository.findByProviderTxnId("TXN-DUPLICATE"))
                .thenReturn(Optional.of(existingTransaction));
        when(paymentMapper.toResponse(payment))
                .thenReturn(org.mockito.Mockito.mock(com.locnguyen.ecommerce.domains.payment.dto.PaymentResponse.class));
        when(webhookLogRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        paymentWebhookService.receiveWebhook("MOMO", "body", "signature");

        verify(orderRepository, never()).findByOrderCodeWithLock(org.mockito.ArgumentMatchers.anyString());
        verify(transactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
        org.assertj.core.api.Assertions.assertThat(webhookLog.getStatus())
                .isEqualTo(com.locnguyen.ecommerce.domains.payment.enums.WebhookLogStatus.IGNORED);
    }

    @Test
    void receiveWebhook_whenSuccessArrivesAfterCancellation_recordsReconciliationWithoutReopeningOrder() {
        PaymentWebhookLog webhookLog = new PaymentWebhookLog();
        Order order = cancelledOnlineOrder(UUID.randomUUID());
        Payment payment = initiatedPayment(order);
        when(webhookLogPersister.createInitialLog("MOMO", "body", "signature"))
                .thenReturn(webhookLog);
        when(providerRegistry.find("MOMO")).thenReturn(Optional.of(paymentProvider));
        when(paymentProvider.verifySignature("body", "signature")).thenReturn(true);
        when(paymentProvider.extractOrderCode("body")).thenReturn(order.getOrderCode());
        when(paymentProvider.extractProviderTxnId("body")).thenReturn("TXN-LATE");
        when(paymentProvider.extractAmount("body")).thenReturn(new BigDecimal("100.00"));
        when(paymentProvider.isSuccess("body")).thenReturn(true);
        when(orderRepository.findByOrderCodeWithLock(order.getOrderCode())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdWithLock(order.getId())).thenReturn(Optional.of(payment));
        when(transactionRepository.findByProviderTxnId("TXN-LATE")).thenReturn(Optional.empty());
        when(paymentRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookLogRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentMapper.toResponse(org.mockito.ArgumentMatchers.any()))
                .thenReturn(org.mockito.Mockito.mock(com.locnguyen.ecommerce.domains.payment.dto.PaymentResponse.class));

        paymentWebhookService.receiveWebhook("MOMO", "body", "signature");

        org.assertj.core.api.Assertions.assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        org.assertj.core.api.Assertions.assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(transactionRepository).save(org.mockito.ArgumentMatchers.argThat((PaymentTransaction txn) ->
                "LATE_SUCCESS_AFTER_CANCEL".equals(txn.getReferenceType())
                        && txn.getNote().contains("reconciliation required")));
    }
}
