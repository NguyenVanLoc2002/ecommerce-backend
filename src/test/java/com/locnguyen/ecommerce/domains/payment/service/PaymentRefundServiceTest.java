package com.locnguyen.ecommerce.domains.payment.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.payment.dto.RefundResponse;
import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import com.locnguyen.ecommerce.domains.payment.entity.PaymentRefund;
import com.locnguyen.ecommerce.domains.payment.enums.RefundStatus;
import com.locnguyen.ecommerce.domains.payment.mapper.PaymentRefundMapper;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentRefundRepository;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentRepository;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentTransactionRepository;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.payment.service.impl.PaymentRefundServiceImpl;
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

    @InjectMocks PaymentRefundServiceImpl refundService;

    // ─── factories ─────────────────────────────────────────────────────────────

    private static UUID uuid(long n) { return new UUID(0L, n); }

    private PaymentRefund refund(String code, RefundStatus status) {
        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", uuid(10));

        PaymentRefund r = new PaymentRefund();
        ReflectionTestUtils.setField(r, "id", uuid(1));
        r.setPayment(payment);
        r.setRefundCode(code);
        r.setAmount(BigDecimal.valueOf(100_000));
        r.setStatus(status);
        r.setRequestedBy("admin");
        return r;
    }

    private RefundResponse stubMapper(PaymentRefund refund) {
        RefundResponse resp = new RefundResponse(
                refund.getId(), refund.getPayment().getId(),
                refund.getRefundCode(), refund.getAmount(),
                null, refund.getStatus(), refund.getProviderRefundId(),
                refund.getRefundedAt(), refund.getRequestedBy(), null, null);
        when(refundMapper.toResponse(refund)).thenReturn(resp);
        return resp;
    }

    // ─── completeRefund ──────────────────────────────────────────────────────

    @Nested
    class CompleteRefund {

        @Test
        void completeRefund_pendingRefund_setsCompletedAndSaves() {
            PaymentRefund refund = refund("REF-001", RefundStatus.PENDING);
            when(refundRepository.findByRefundCode("REF-001")).thenReturn(Optional.of(refund));
            when(refundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            stubMapper(refund);

            refundService.completeRefund("REF-001", "PROVIDER-TXN-123");

            ArgumentCaptor<PaymentRefund> captor = ArgumentCaptor.forClass(PaymentRefund.class);
            verify(refundRepository).save(captor.capture());
            PaymentRefund saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(RefundStatus.COMPLETED);
            assertThat(saved.getProviderRefundId()).isEqualTo("PROVIDER-TXN-123");
            assertThat(saved.getRefundedAt()).isNotNull();
        }

        @Test
        void completeRefund_pendingRefund_withNullProviderRefundId_succeeds() {
            PaymentRefund refund = refund("REF-002", RefundStatus.PENDING);
            when(refundRepository.findByRefundCode("REF-002")).thenReturn(Optional.of(refund));
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
            when(refundRepository.findByRefundCode("REF-004")).thenReturn(Optional.of(refund));

            assertThatThrownBy(() -> refundService.completeRefund("REF-004", null))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_REFUND_INVALID_STATUS);
        }

        @Test
        void completeRefund_cancelledStatus_throwsInvalidStatus() {
            PaymentRefund refund = refund("REF-005", RefundStatus.CANCELLED);
            when(refundRepository.findByRefundCode("REF-005")).thenReturn(Optional.of(refund));

            assertThatThrownBy(() -> refundService.completeRefund("REF-005", null))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_REFUND_INVALID_STATUS);
        }

        @Test
        void completeRefund_processingStatus_throwsInvalidStatus() {
            PaymentRefund refund = refund("REF-006", RefundStatus.PROCESSING);
            when(refundRepository.findByRefundCode("REF-006")).thenReturn(Optional.of(refund));

            assertThatThrownBy(() -> refundService.completeRefund("REF-006", null))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_REFUND_INVALID_STATUS);
        }
    }
}
