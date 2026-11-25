package com.locnguyen.ecommerce.domains.payment.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.payment.dto.RefundResponse;
import com.locnguyen.ecommerce.domains.payment.dto.RefundRequest;
import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import com.locnguyen.ecommerce.domains.payment.entity.PaymentRefund;
import com.locnguyen.ecommerce.domains.payment.enums.PaymentRecordStatus;
import com.locnguyen.ecommerce.domains.payment.enums.RefundStatus;
import com.locnguyen.ecommerce.domains.payment.enums.TransactionStatus;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.payment.mapper.PaymentRefundMapper;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentRefundRepository;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentRepository;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentTransactionRepository;
import jakarta.persistence.EntityManager;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.payment.service.impl.PaymentRefundServiceImpl;
import com.locnguyen.ecommerce.domains.invoice.service.InvoiceService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PaymentRefundService}.
 *
 * Covers completeRefund: success path, idempotent on COMPLETED, not-found, non-PENDING rejection.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentRefundServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentRefundRepository refundRepository;
    @Mock PaymentTransactionRepository transactionRepository;
    @Mock OrderRepository orderRepository;
    @Mock PaymentRefundMapper refundMapper;
    @Mock EntityManager entityManager;
    @Mock InvoiceService invoiceService;

    @Mock com.locnguyen.ecommerce.infrastructure.payment.paypal.PaypalClient paypalClient;

    @InjectMocks PaymentRefundServiceImpl refundService;

    // ─── factories ─────────────────────────────────────────────────────────────

    private static UUID uuid(long n) { return new UUID(0L, n); }

    private PaymentRefund refund(String code, RefundStatus status) {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", uuid(20));
        order.setPaymentStatus(com.locnguyen.ecommerce.domains.order.enums.PaymentStatus.PAID);
        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", uuid(10));
        payment.setOrder(order);
        payment.setAmount(BigDecimal.valueOf(200_000));
        payment.setMethod(PaymentMethod.ONLINE);
        payment.setStatus(PaymentRecordStatus.PAID);

        PaymentRefund r = new PaymentRefund();
        ReflectionTestUtils.setField(r, "id", uuid(1));
        r.setPayment(payment);
        r.setRefundCode(code);
        r.setAmount(BigDecimal.valueOf(100_000));
        r.setStatus(status);
        r.setRequestedBy("admin");
        return r;
    }

    private void stubLocks(Payment payment) {
        when(paymentRepository.findOrderIdByPaymentId(payment.getId())).thenReturn(Optional.of(uuid(20)));
        when(orderRepository.findByIdWithLock(uuid(20))).thenReturn(Optional.of(payment.getOrder()));
        when(paymentRepository.findByIdWithLock(payment.getId())).thenReturn(Optional.of(payment));
    }

    @Test
    void codEvidenceMustBeConfirmedByAnotherOperator() {
        var refund = refund("REF-COD", RefundStatus.PENDING);
        var payment = refund.getPayment();
        payment.setMethod(PaymentMethod.COD);
        stubReturnLocks(refund);
        when(refundRepository.saveAndFlush(refund)).thenReturn(refund);
        when(refundRepository.save(refund)).thenReturn(refund);
        when(refundRepository.findByPaymentIdAndStatusIn(eq(payment.getId()), anyList()))
                .thenReturn(java.util.List.of(refund));

        refundService.submitCodReturnRefundEvidence(uuid(20), uuid(2), "TRANSFER01", "maker");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(payment.getStatus()).isEqualTo(PaymentRecordStatus.PAID);
        assertThatThrownBy(() -> refundService.confirmCodReturnRefund(uuid(20), uuid(2), "MAKER"))
                .isInstanceOf(AppException.class);
        refundService.confirmCodReturnRefund(uuid(20), uuid(2), "checker");
        refundService.confirmCodReturnRefund(uuid(20), uuid(2), "checker");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        verify(transactionRepository, times(1)).save(any());
    }

    @Test
    void onlyMatchingCompletedPaypalEvidenceSettlesOnce() {
        var refund = refund("REF-PP", RefundStatus.PENDING);
        var payment = refund.getPayment();
        payment.setProviderCurrency("USD");
        payment.setProviderExchangeRate(BigDecimal.valueOf(25_000));
        stubReturnLocks(refund);
        var capture = new com.locnguyen.ecommerce.domains.payment.entity.PaymentTransaction();
        capture.setProvider(com.locnguyen.ecommerce.domains.payment.enums.PaymentProviderType.PAYPAL);
        capture.setStatus(TransactionStatus.SUCCESS);
        capture.setReferenceType("CAPTURE");
        capture.setProviderTxnId("CAPTURE-1");
        when(transactionRepository.findByPaymentIdOrderByCreatedAtDesc(payment.getId()))
                .thenReturn(java.util.List.of(capture));
        var link = mock(com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalLink.class);
        when(link.getRel()).thenReturn("up");
        when(link.getHref()).thenReturn("https://api.paypal.com/v2/payments/captures/CAPTURE-1");
        when(paypalClient.getRefund("PS-1")).thenReturn(paypalDetails("PENDING", 4, link),
                paypalDetails("COMPLETED", 3, link), paypalDetails("COMPLETED", 4, link));
        when(refundRepository.save(refund)).thenReturn(refund);
        when(refundRepository.findByPaymentIdAndStatusIn(eq(payment.getId()), anyList()))
                .thenReturn(java.util.List.of(refund));
        refundService.verifyPaypalReturnRefund(uuid(20), uuid(2), "PS-1");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThatThrownBy(() -> refundService.verifyPaypalReturnRefund(uuid(20), uuid(2), "PS-1"))
                .isInstanceOf(AppException.class);
        verify(transactionRepository, never()).save(any());
        refundService.verifyPaypalReturnRefund(uuid(20), uuid(2), "PS-1");
        refundService.verifyPaypalReturnRefund(uuid(20), uuid(2), "PS-1");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getProviderRefundId()).isEqualTo("PS-1");
        verify(transactionRepository, times(1)).save(any());
        verify(paypalClient, times(3)).getRefund("PS-1");
    }

    private com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalRefundDetails paypalDetails(
            String status, int amount, com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalLink link) {
        var money = new com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalRefundDetails.Amount(
                "USD", BigDecimal.valueOf(amount));
        return new com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalRefundDetails(
                "PS-1", status, "REF-PP", money, java.util.List.of(link));
    }

    private void stubReturnLocks(PaymentRefund refund) {
        Payment payment = refund.getPayment();
        refund.setReturnId(uuid(2));
        stubLocks(payment);
        when(paymentRepository.findByOrderIdWithLock(uuid(20))).thenReturn(Optional.of(payment));
        when(refundRepository.findByReturnId(uuid(2))).thenReturn(Optional.of(refund));
    }

    private RefundResponse stubMapper(PaymentRefund refund) {
        RefundResponse resp = new RefundResponse(
                refund.getId(), refund.getPayment().getId(),
                refund.getRefundCode(), refund.getAmount(),
                null, refund.getStatus(), refund.getProviderRefundId(),
                refund.getRefundedAt(), refund.getRequestedBy(), null, null, null, null, refund.getPayment().getMethod());
        when(refundMapper.toResponse(refund)).thenReturn(resp);
        return resp;
    }

    // ─── completeRefund ──────────────────────────────────────────────────────

    @Nested
    class CompleteRefund {

        @Test
        void completeRefund_pendingRefund_setsCompletedAndSaves() {
            PaymentRefund refund = refund("REF-001", RefundStatus.PENDING);
            stubLocks(refund.getPayment());
            when(refundRepository.findByRefundCode("REF-001")).thenReturn(Optional.of(refund));
            when(refundRepository.findByPaymentIdAndStatusIn(eq(uuid(10)), anyList()))
                    .thenAnswer(inv -> refund.getStatus() == RefundStatus.COMPLETED ? java.util.List.of(refund) : java.util.List.of());
            when(refundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            stubMapper(refund);

            refundService.completeRefund("REF-001", "PROVIDER-TXN-123");

            ArgumentCaptor<PaymentRefund> captor = ArgumentCaptor.forClass(PaymentRefund.class);
            verify(refundRepository).save(captor.capture());
            PaymentRefund saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(RefundStatus.COMPLETED);
            assertThat(saved.getProviderRefundId()).isEqualTo("PROVIDER-TXN-123");
            assertThat(saved.getRefundedAt()).isNotNull();
            assertThat(refund.getPayment().getStatus()).isEqualTo(PaymentRecordStatus.PARTIALLY_REFUNDED);
            assertThat(refund.getPayment().getOrder().getPaymentStatus())
                    .isEqualTo(com.locnguyen.ecommerce.domains.order.enums.PaymentStatus.PARTIALLY_REFUNDED);
            verify(transactionRepository).save(argThat(txn -> txn.getStatus() == TransactionStatus.REFUNDED));
        }

        @Test
        void completeRefund_pendingRefund_withNullProviderRefundId_succeeds() {
            PaymentRefund refund = refund("REF-002", RefundStatus.PENDING);
            stubLocks(refund.getPayment());
            when(refundRepository.findByRefundCode("REF-002")).thenReturn(Optional.of(refund));
            when(refundRepository.findByPaymentIdAndStatusIn(eq(uuid(10)), anyList()))
                    .thenAnswer(inv -> java.util.List.of(refund));
            when(refundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            stubMapper(refund);

            refundService.completeRefund("REF-002", null);

            ArgumentCaptor<PaymentRefund> captor = ArgumentCaptor.forClass(PaymentRefund.class);
            verify(refundRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(RefundStatus.COMPLETED);
            assertThat(captor.getValue().getProviderRefundId()).isNull();
        }

        @Test
        void completeRefund_alreadyCompleted_returnsExistingWithoutSave() {
            PaymentRefund refund = refund("REF-003", RefundStatus.COMPLETED);
            stubLocks(refund.getPayment());
            when(refundRepository.findByRefundCode("REF-003")).thenReturn(Optional.of(refund));
            stubMapper(refund);

            RefundResponse result = refundService.completeRefund("REF-003", "any-id");

            verify(refundRepository, never()).save(any());
            assertThat(result.status()).isEqualTo(RefundStatus.COMPLETED);
        }

        @Test
        void completeRefund_refundNotFound_throwsPaymentRefundNotFound() {
            when(refundRepository.findByRefundCode("MISSING")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refundService.completeRefund("MISSING", null))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_REFUND_NOT_FOUND);
        }

        @Test
        void completeRefund_failedStatus_throwsInvalidStatus() {
            PaymentRefund refund = refund("REF-004", RefundStatus.FAILED);
            stubLocks(refund.getPayment());
            when(refundRepository.findByRefundCode("REF-004")).thenReturn(Optional.of(refund));

            assertThatThrownBy(() -> refundService.completeRefund("REF-004", null))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_REFUND_INVALID_STATUS);
        }

        @Test
        void completeRefund_cancelledStatus_throwsInvalidStatus() {
            PaymentRefund refund = refund("REF-005", RefundStatus.CANCELLED);
            stubLocks(refund.getPayment());
            when(refundRepository.findByRefundCode("REF-005")).thenReturn(Optional.of(refund));

            assertThatThrownBy(() -> refundService.completeRefund("REF-005", null))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_REFUND_INVALID_STATUS);
        }

        @Test
        void completeRefund_processingStatus_throwsInvalidStatus() {
            PaymentRefund refund = refund("REF-006", RefundStatus.PROCESSING);
            stubLocks(refund.getPayment());
            when(refundRepository.findByRefundCode("REF-006")).thenReturn(Optional.of(refund));

            assertThatThrownBy(() -> refundService.completeRefund("REF-006", null))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_REFUND_INVALID_STATUS);
        }

        @Test
        void completeRefund_fullAmountPending_marksPaymentAndOrderRefundedOnlyAfterCompletion() {
            PaymentRefund refund = refund("REF-007", RefundStatus.PENDING);
            refund.setAmount(refund.getPayment().getAmount());
            stubLocks(refund.getPayment());
            when(refundRepository.findByRefundCode("REF-007")).thenReturn(Optional.of(refund));
            when(refundRepository.findByPaymentIdAndStatusIn(eq(uuid(10)), anyList()))
                    .thenAnswer(inv -> refund.getStatus() == RefundStatus.COMPLETED ? java.util.List.of(refund) : java.util.List.of());
            when(refundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            stubMapper(refund);

            refundService.completeRefund("REF-007", "PROVIDER-FULL-REFUND");

            assertThat(refund.getPayment().getStatus()).isEqualTo(PaymentRecordStatus.REFUNDED);
            assertThat(refund.getPayment().getOrder().getPaymentStatus())
                    .isEqualTo(com.locnguyen.ecommerce.domains.order.enums.PaymentStatus.REFUNDED);
            verify(orderRepository).save(refund.getPayment().getOrder());
            verify(invoiceService).recordRefundByOrderId(
                    uuid(20), new BigDecimal("200000"), new BigDecimal("200000"));
        }
    }

    @Nested
    class InitiateRefund {

        @Test
        void initiateRefund_fullAmountPending_keepsPaymentAndOrderPaidAndReservesAmount() {
            PaymentRefund existingPending = refund("REF-PENDING", RefundStatus.PENDING);
            Payment payment = existingPending.getPayment();
            when(paymentRepository.findById(uuid(10))).thenReturn(Optional.of(payment));
            stubLocks(payment);
            when(refundRepository.findByPaymentIdAndStatusIn(eq(uuid(10)), anyList()))
                    .thenReturn(java.util.List.of());
            when(refundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            refundService.initiateRefund(uuid(10), new RefundRequest(BigDecimal.valueOf(200_000), "return", null));

            assertThat(payment.getStatus()).isEqualTo(PaymentRecordStatus.PAID);
            assertThat(payment.getOrder().getPaymentStatus())
                    .isEqualTo(com.locnguyen.ecommerce.domains.order.enums.PaymentStatus.PAID);
            verify(orderRepository, never()).save(any());
            verify(transactionRepository).save(argThat(txn -> txn.getStatus() == TransactionStatus.INITIATED));
            verify(orderRepository).findByIdWithLock(uuid(20));
            verify(paymentRepository).findByIdWithLock(uuid(10));
        }

        @Test
        void initiateRefund_pendingAmountAlreadyReserved_rejectsOverRefund() {
            PaymentRefund existingPending = refund("REF-PENDING", RefundStatus.PENDING);
            Payment payment = existingPending.getPayment();
            when(paymentRepository.findById(uuid(10))).thenReturn(Optional.of(payment));
            stubLocks(payment);
            when(refundRepository.findByPaymentIdAndStatusIn(eq(uuid(10)), anyList()))
                    .thenReturn(java.util.List.of(existingPending));

            assertThatThrownBy(() -> refundService.initiateRefund(
                    uuid(10), new RefundRequest(BigDecimal.valueOf(100_001), "extra", null)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_REFUND_AMOUNT_EXCEEDED);
        }
    }
}
