package com.locnguyen.ecommerce.domains.payment.service;

import com.locnguyen.ecommerce.common.config.AppProperties;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.idempotency.entity.IdempotencyKey;
import com.locnguyen.ecommerce.domains.idempotency.enums.IdempotencyStatus;
import com.locnguyen.ecommerce.domains.idempotency.service.IdempotencyService;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.payment.dto.InitPaymentRequest;
import com.locnguyen.ecommerce.domains.payment.dto.PaymentResponse;
import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import com.locnguyen.ecommerce.domains.payment.enums.PaymentRecordStatus;
import com.locnguyen.ecommerce.domains.payment.mapper.PaymentMapper;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProviderRegistry;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentRepository;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentTransactionRepository;
import com.locnguyen.ecommerce.domains.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the idempotency wrapper around {@link PaymentService#initiateOnlinePayment}.
 *
 * Verifies that:
 * - A COMPLETED replay returns the existing payment without re-executing
 * - IDEMPOTENCY_KEY_CONFLICT propagates to the caller
 * - IDEMPOTENCY_REQUEST_IN_PROGRESS propagates to the caller
 * - markComplete is called after successful initiation
 * - markFailed is called on AppException during initiation
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentIdempotencyTest {

    private static UUID uuid(long n) { return new UUID(0L, n); }

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentTransactionRepository transactionRepository;
    @Mock OrderRepository orderRepository;
    @Mock PaymentMapper paymentMapper;
    @Mock IdempotencyService idempotencyService;
    @Mock PaymentProviderRegistry providerRegistry;
    @Mock AppProperties appProperties;

    @InjectMocks PaymentServiceImpl paymentService;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = mock(Customer.class);
        when(customer.getId()).thenReturn(uuid(1));
    }

    private IdempotencyKey processingRecord() {
        IdempotencyKey k = new IdempotencyKey();
        ReflectionTestUtils.setField(k, "id", 1L);
        k.setStatus(IdempotencyStatus.PROCESSING);
        return k;
    }

    private IdempotencyKey completedRecord(UUID paymentId) {
        IdempotencyKey k = new IdempotencyKey();
        ReflectionTestUtils.setField(k, "id", 1L);
        k.setStatus(IdempotencyStatus.COMPLETED);
        k.setResourceId(paymentId.toString());
        return k;
    }

    private Order onlineOrder(UUID orderId) {
        Order o = new Order();
        ReflectionTestUtils.setField(o, "id", orderId);
        o.setCustomer(customer);
        o.setOrderCode("ORD-TEST");
        o.setPaymentMethod(PaymentMethod.ONLINE);
        o.setPaymentStatus(PaymentStatus.PENDING);
        o.setTotalAmount(new BigDecimal("200000"));
        return o;
    }

    private Payment payment(UUID id, PaymentRecordStatus status) {
        Payment p = new Payment();
        ReflectionTestUtils.setField(p, "id", id);
        p.setStatus(status);
        return p;
    }

    // ─── Idempotency gate behavior ────────────────────────────────────────────

    @Nested
    class IdempotencyGate {

        @Test
        void replays_existing_payment_when_COMPLETED_record_found() {
            IdempotencyKey completed = completedRecord(uuid(50));
            when(idempotencyService.findOrCreateProcessing(any(), any(), any(), any()))
                    .thenReturn(completed);
            Payment existingPayment = payment(uuid(50), PaymentRecordStatus.INITIATED);
            when(paymentRepository.findById(uuid(50))).thenReturn(Optional.of(existingPayment));
            when(paymentMapper.toResponse(existingPayment)).thenReturn(mock(PaymentResponse.class));

            paymentService.initiateOnlinePayment(uuid(1), customer, new InitPaymentRequest(), "my-key");

            // No order lookup, no new payment creation
            verify(orderRepository, never()).findById(any());
            verify(paymentRepository, never()).existsByOrderId(any());
        }

        @Test
        void throws_PAYMENT_NOT_FOUND_when_replay_payment_missing() {
            IdempotencyKey completed = completedRecord(uuid(50));
            when(idempotencyService.findOrCreateProcessing(any(), any(), any(), any()))
                    .thenReturn(completed);
            when(paymentRepository.findById(uuid(50))).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    paymentService.initiateOnlinePayment(uuid(1), customer, new InitPaymentRequest(), "my-key"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
        }

        @Test
        void propagates_IDEMPOTENCY_KEY_CONFLICT_from_service() {
            when(idempotencyService.findOrCreateProcessing(any(), any(), any(), any()))
                    .thenThrow(new AppException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));

            assertThatThrownBy(() ->
                    paymentService.initiateOnlinePayment(uuid(1), customer, new InitPaymentRequest(), "my-key"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        @Test
        void propagates_IDEMPOTENCY_REQUEST_IN_PROGRESS_from_service() {
            when(idempotencyService.findOrCreateProcessing(any(), any(), any(), any()))
                    .thenThrow(new AppException(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS));

            assertThatThrownBy(() ->
                    paymentService.initiateOnlinePayment(uuid(1), customer, new InitPaymentRequest(), "my-key"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
        }

        @Test
        void calls_markComplete_after_successful_initiation() {
            when(idempotencyService.findOrCreateProcessing(any(), any(), any(), any()))
                    .thenReturn(processingRecord());
            Order o = onlineOrder(uuid(1));
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(o));
            when(paymentRepository.existsByOrderId(uuid(1))).thenReturn(false);
            when(paymentRepository.save(any())).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                ReflectionTestUtils.setField(p, "id", uuid(50));
                return p;
            });
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentMapper.toResponse(any())).thenReturn(mock(PaymentResponse.class));

            paymentService.initiateOnlinePayment(uuid(1), customer, new InitPaymentRequest(), "my-key");

            verify(idempotencyService).markComplete(
                    eq(1L), eq("PAYMENT"), eq(uuid(50).toString()), eq(201));
        }

        @Test
        void calls_markFailed_when_order_not_found() {
            when(idempotencyService.findOrCreateProcessing(any(), any(), any(), any()))
                    .thenReturn(processingRecord());
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    paymentService.initiateOnlinePayment(uuid(1), customer, new InitPaymentRequest(), "my-key"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_NOT_FOUND);

            verify(idempotencyService).markFailed(eq(1L), eq(ErrorCode.ORDER_NOT_FOUND.getCode()));
        }

        @Test
        void duplicate_initiation_with_same_key_does_not_create_second_payment() {
            // First call returns COMPLETED (simulating a recorded completed initiation)
            IdempotencyKey completed = completedRecord(uuid(50));
            when(idempotencyService.findOrCreateProcessing(any(), any(), any(), any()))
                    .thenReturn(completed);
            Payment existingPayment = payment(uuid(50), PaymentRecordStatus.INITIATED);
            when(paymentRepository.findById(uuid(50))).thenReturn(Optional.of(existingPayment));
            when(paymentMapper.toResponse(existingPayment)).thenReturn(mock(PaymentResponse.class));

            paymentService.initiateOnlinePayment(uuid(1), customer, new InitPaymentRequest(), "my-key");
            paymentService.initiateOnlinePayment(uuid(1), customer, new InitPaymentRequest(), "my-key");

            // Never attempted to create a new payment record
            verify(paymentRepository, never()).save(any());
            verify(transactionRepository, never()).save(any());
        }
    }
}
