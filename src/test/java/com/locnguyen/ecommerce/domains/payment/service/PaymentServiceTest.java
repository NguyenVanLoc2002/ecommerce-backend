package com.locnguyen.ecommerce.domains.payment.service;

import com.locnguyen.ecommerce.common.config.AppProperties;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.payment.dto.InitPaymentRequest;
import com.locnguyen.ecommerce.domains.payment.dto.PaymentCallbackRequest;
import com.locnguyen.ecommerce.domains.payment.dto.PaymentResponse;
import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import com.locnguyen.ecommerce.domains.payment.entity.PaymentTransaction;
import com.locnguyen.ecommerce.domains.payment.enums.PaymentRecordStatus;
import com.locnguyen.ecommerce.domains.payment.enums.TransactionStatus;
import com.locnguyen.ecommerce.domains.payment.mapper.PaymentMapper;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProviderRegistry;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentRepository;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentTransactionRepository;
import com.locnguyen.ecommerce.domains.payment.service.impl.PaymentServiceImpl;
import com.locnguyen.ecommerce.domains.idempotency.entity.IdempotencyKey;
import com.locnguyen.ecommerce.domains.idempotency.enums.IdempotencyStatus;
import com.locnguyen.ecommerce.domains.idempotency.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
/**
 * Unit tests for {@link PaymentService}.
 *
 * Tests cover:
 * - createCodPayment: creation, idempotency
 * - completeCodPayment: status transition, idempotency, non-PENDING guard, order sync
 * - initiateOnlinePayment: ownership, method check, in-flight idempotency, failed retry, terminal guards
 * - processCallback: idempotency on PAID, duplicate providerTxnId, refunded guard, success/failure paths
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    private static UUID uuid(long n) { return new UUID(0L, n); }

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentTransactionRepository transactionRepository;
    @Mock OrderRepository orderRepository;
    @Mock PaymentMapper paymentMapper;
    @Mock IdempotencyService idempotencyService;
    @Mock PaymentProviderRegistry providerRegistry;
    @Mock AppProperties appProperties;

    @InjectMocks PaymentServiceImpl paymentService;

    @BeforeEach
    void stubIdempotency() {
        // Default stub: return a PROCESSING record so all InitiateOnlinePayment tests
        // bypass the idempotency gate and reach the business logic.
        IdempotencyKey processingIdem = new IdempotencyKey();
        ReflectionTestUtils.setField(processingIdem, "id", 1L);
        processingIdem.setStatus(IdempotencyStatus.PROCESSING);
        when(idempotencyService.findOrCreateProcessing(any(), any(), any(), any()))
                .thenReturn(processingIdem);
    }

    // ─── factories ───────────────────────────────────────────────────────────

    private Customer customer(UUID id) {
        Customer c = mock(Customer.class);
        when(c.getId()).thenReturn(id);
        return c;
    }

    private Order order(UUID id, Customer customer, PaymentMethod method, BigDecimal total) {
        Order o = new Order();
        setId(o, id);
        o.setCustomer(customer);
        o.setOrderCode("ORD202604060001");
        o.setPaymentMethod(method);
        o.setPaymentStatus(PaymentStatus.PENDING);
        o.setTotalAmount(total);
        return o;
    }

    private Payment payment(UUID id, Order order, PaymentRecordStatus status) {
        Payment p = new Payment();
        setId(p, id);
        p.setOrder(order);
        p.setPaymentCode("PAY202604060001");
        p.setMethod(order.getPaymentMethod());
        p.setStatus(status);
        p.setAmount(order.getTotalAmount());
        return p;
    }

    private PaymentCallbackRequest callback(String orderCode, String status, String providerTxnId) {
        PaymentCallbackRequest req = new PaymentCallbackRequest();
        req.setOrderCode(orderCode);
        req.setStatus(status);
        req.setProvider("VNPAY");
        req.setProviderTxnId(providerTxnId);
        req.setPayload("{\"txn\":\"12345\"}");
        return req;
    }

    private static void setId(Object entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }

    // ─── createCodPayment ─────────────────────────────────────────────────────

    @Nested
    class CreateCodPayment {

        @Test
        void creates_payment_with_PENDING_status() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.COD, new BigDecimal("200000"));
            when(paymentRepository.existsByOrderId(uuid(1))).thenReturn(false);
            when(paymentRepository.save(any())).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                setId(p, uuid(10));
                return p;
            });
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentMapper.toResponse(any())).thenReturn(mock(PaymentResponse.class));

            paymentService.createCodPayment(o);

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PaymentRecordStatus.PENDING);
            assertThat(captor.getValue().getMethod()).isEqualTo(PaymentMethod.COD);
        }

        @Test
        void records_INITIATED_transaction_on_creation() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.COD, new BigDecimal("200000"));
            when(paymentRepository.existsByOrderId(uuid(1))).thenReturn(false);
            when(paymentRepository.save(any())).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                setId(p, uuid(10));
                return p;
            });
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentMapper.toResponse(any())).thenReturn(mock(PaymentResponse.class));

            paymentService.createCodPayment(o);

            verify(transactionRepository).save(argThat(
                    t -> t.getStatus() == TransactionStatus.INITIATED));
        }

        @Test
        void idempotent_returns_existing_when_payment_already_exists() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.COD, new BigDecimal("200000"));
            Payment existing = payment(uuid(10), o, PaymentRecordStatus.PENDING);
            when(paymentRepository.existsByOrderId(uuid(1))).thenReturn(true);
            when(paymentRepository.findByOrderId(uuid(1))).thenReturn(Optional.of(existing));
            when(paymentMapper.toResponse(existing)).thenReturn(mock(PaymentResponse.class));

            paymentService.createCodPayment(o);

            // No new payment should be saved
            verify(paymentRepository, never()).save(any());
        }
    }

    // ─── completeCodPayment ───────────────────────────────────────────────────

    @Nested
    class CompleteCodPayment {

        @Test
        void transitions_PENDING_to_PAID_and_syncs_order() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.COD, new BigDecimal("200000"));
            Payment p = payment(uuid(10), o, PaymentRecordStatus.PENDING);
            when(paymentRepository.findByOrderId(uuid(1))).thenReturn(Optional.of(p));
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentMapper.toResponse(any())).thenReturn(mock(PaymentResponse.class));

            paymentService.completeCodPayment(uuid(1));

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PaymentRecordStatus.PAID);
            assertThat(captor.getValue().getPaidAt()).isNotNull();

            // Order must also be updated
            verify(orderRepository).save(argThat(order ->
                    order.getPaymentStatus() == PaymentStatus.PAID));
        }

        @Test
        void idempotent_returns_silently_when_already_PAID() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.COD, new BigDecimal("200000"));
            Payment p = payment(uuid(10), o, PaymentRecordStatus.PAID);
            when(paymentRepository.findByOrderId(uuid(1))).thenReturn(Optional.of(p));
            when(paymentMapper.toResponse(any())).thenReturn(mock(PaymentResponse.class));

            paymentService.completeCodPayment(uuid(1));

            // No further save operations
            verify(paymentRepository, never()).save(any());
            verify(orderRepository, never()).save(any());
        }

        @Test
        void throws_PAYMENT_ALREADY_PROCESSED_when_status_is_REFUNDED() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.COD, new BigDecimal("200000"));
            Payment p = payment(uuid(10), o, PaymentRecordStatus.REFUNDED);
            when(paymentRepository.findByOrderId(uuid(1))).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> paymentService.completeCodPayment(uuid(1)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        @Test
        void throws_PAYMENT_NOT_FOUND_when_no_payment_for_order() {
            when(paymentRepository.findByOrderId(uuid(99))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.completeCodPayment(uuid(99)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
        }

        @Test
        void records_SUCCESS_transaction_on_completion() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.COD, new BigDecimal("200000"));
            Payment p = payment(uuid(10), o, PaymentRecordStatus.PENDING);
            when(paymentRepository.findByOrderId(uuid(1))).thenReturn(Optional.of(p));
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentMapper.toResponse(any())).thenReturn(mock(PaymentResponse.class));

            paymentService.completeCodPayment(uuid(1));

            verify(transactionRepository).save(argThat(
                    t -> t.getStatus() == TransactionStatus.SUCCESS));
        }
    }

    // ─── initiateOnlinePayment ────────────────────────────────────────────────

    @Nested
    class InitiateOnlinePayment {

        private InitPaymentRequest initRequest() {
            InitPaymentRequest req = new InitPaymentRequest();
            req.setProvider("VNPAY");
            return req;
        }

        @Test
        void throws_ORDER_NOT_FOUND_when_order_missing() {
            Customer cust = customer(uuid(1));
            when(orderRepository.findById(uuid(99))).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    paymentService.initiateOnlinePayment(uuid(99), cust, initRequest(), "idem-key"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        void throws_ORDER_NOT_FOUND_when_order_belongs_to_another_customer() {
            Customer cust = customer(uuid(1));
            Customer otherCust = customer(uuid(999));
            Order o = order(uuid(1), otherCust, PaymentMethod.ONLINE, new BigDecimal("200000"));
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(o));

            assertThatThrownBy(() ->
                    paymentService.initiateOnlinePayment(uuid(1), cust, initRequest(), "idem-key"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        void throws_BAD_REQUEST_when_order_payment_method_is_COD() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.COD, new BigDecimal("200000"));
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(o));

            assertThatThrownBy(() ->
                    paymentService.initiateOnlinePayment(uuid(1), cust, initRequest(), "idem-key"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.BAD_REQUEST);
        }

        @Test
        void creates_new_payment_with_INITIATED_status_on_first_call() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.ONLINE, new BigDecimal("200000"));
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(o));
            when(paymentRepository.existsByOrderId(uuid(1))).thenReturn(false);
            when(paymentRepository.save(any())).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                setId(p, uuid(10));
                return p;
            });
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentMapper.toResponse(any())).thenReturn(mock(PaymentResponse.class));

            paymentService.initiateOnlinePayment(uuid(1), cust, initRequest(), "idem-key");

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PaymentRecordStatus.INITIATED);
        }

        @Test
        void idempotent_returns_existing_when_payment_is_INITIATED() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.ONLINE, new BigDecimal("200000"));
            Payment existing = payment(uuid(10), o, PaymentRecordStatus.INITIATED);
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(o));
            when(paymentRepository.existsByOrderId(uuid(1))).thenReturn(true);
            when(paymentRepository.findByOrderId(uuid(1))).thenReturn(Optional.of(existing));
            when(paymentMapper.toResponse(any())).thenReturn(mock(PaymentResponse.class));

            paymentService.initiateOnlinePayment(uuid(1), cust, initRequest(), "idem-key");

            verify(paymentRepository, never()).save(any());
        }

        @Test
        void allows_retry_when_previous_payment_is_FAILED() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.ONLINE, new BigDecimal("200000"));
            Payment failed = payment(uuid(10), o, PaymentRecordStatus.FAILED);
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(o));
            when(paymentRepository.existsByOrderId(uuid(1))).thenReturn(true);
            when(paymentRepository.findByOrderId(uuid(1))).thenReturn(Optional.of(failed));
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentMapper.toResponse(any())).thenReturn(mock(PaymentResponse.class));

            paymentService.initiateOnlinePayment(uuid(1), cust, initRequest(), "idem-key");

            verify(paymentRepository).save(argThat(
                    p -> p.getStatus() == PaymentRecordStatus.INITIATED));
        }

        @Test
        void throws_PAYMENT_ALREADY_PROCESSED_when_payment_is_already_PAID() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.ONLINE, new BigDecimal("200000"));
            Payment paid = payment(uuid(10), o, PaymentRecordStatus.PAID);
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(o));
            when(paymentRepository.existsByOrderId(uuid(1))).thenReturn(true);
            when(paymentRepository.findByOrderId(uuid(1))).thenReturn(Optional.of(paid));

            assertThatThrownBy(() ->
                    paymentService.initiateOnlinePayment(uuid(1), cust, initRequest(), "idem-key"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        @Test
        void throws_PAYMENT_ALREADY_PROCESSED_when_payment_is_REFUNDED() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.ONLINE, new BigDecimal("200000"));
            Payment refunded = payment(uuid(10), o, PaymentRecordStatus.REFUNDED);
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(o));
            when(paymentRepository.existsByOrderId(uuid(1))).thenReturn(true);
            when(paymentRepository.findByOrderId(uuid(1))).thenReturn(Optional.of(refunded));

            assertThatThrownBy(() ->
                    paymentService.initiateOnlinePayment(uuid(1), cust, initRequest(), "idem-key"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }
    }

    // ─── processCallback ─────────────────────────────────────────────────────

    @Nested
    class ProcessCallback {

        @Test
        void throws_PAYMENT_CALLBACK_INVALID_when_order_code_not_found() {
            when(orderRepository.findByOrderCode("GHOST")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    paymentService.processCallback(callback("GHOST", "SUCCESS", "TXN001")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_CALLBACK_INVALID);
        }

        @Test
        void throws_PAYMENT_NOT_FOUND_when_no_payment_for_order() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.ONLINE, new BigDecimal("200000"));
            when(orderRepository.findByOrderCode("ORD202604060001")).thenReturn(Optional.of(o));
            when(paymentRepository.findByOrderIdWithLock(uuid(1))).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    paymentService.processCallback(callback("ORD202604060001", "SUCCESS", "TXN001")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
        }

        @Test
        void idempotent_returns_silently_when_payment_already_PAID() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.ONLINE, new BigDecimal("200000"));
            Payment p = payment(uuid(10), o, PaymentRecordStatus.PAID);
            when(orderRepository.findByOrderCode("ORD202604060001")).thenReturn(Optional.of(o));
            when(paymentRepository.findByOrderIdWithLock(uuid(1))).thenReturn(Optional.of(p));
            when(paymentMapper.toResponse(any())).thenReturn(mock(PaymentResponse.class));

            paymentService.processCallback(callback("ORD202604060001", "SUCCESS", "TXN001"));

            verify(paymentRepository, never()).save(any());
            verify(orderRepository, never()).save(any());
        }

        @Test
        void idempotent_returns_silently_when_providerTxnId_already_processed() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.ONLINE, new BigDecimal("200000"));
            Payment p = payment(uuid(10), o, PaymentRecordStatus.INITIATED);
            when(orderRepository.findByOrderCode("ORD202604060001")).thenReturn(Optional.of(o));
            when(paymentRepository.findByOrderIdWithLock(uuid(1))).thenReturn(Optional.of(p));
            when(transactionRepository.findByProviderTxnId("TXN-DUPLICATE"))
                    .thenReturn(Optional.of(mock(PaymentTransaction.class)));
            when(paymentMapper.toResponse(any())).thenReturn(mock(PaymentResponse.class));

            paymentService.processCallback(callback("ORD202604060001", "SUCCESS", "TXN-DUPLICATE"));

            verify(paymentRepository, never()).save(any());
        }

        @Test
        void throws_PAYMENT_ALREADY_PROCESSED_when_payment_is_REFUNDED() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.ONLINE, new BigDecimal("200000"));
            Payment p = payment(uuid(10), o, PaymentRecordStatus.REFUNDED);
            when(orderRepository.findByOrderCode("ORD202604060001")).thenReturn(Optional.of(o));
            when(paymentRepository.findByOrderIdWithLock(uuid(1))).thenReturn(Optional.of(p));

            assertThatThrownBy(() ->
                    paymentService.processCallback(callback("ORD202604060001", "SUCCESS", "TXN001")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        @Test
        void SUCCESS_callback_sets_payment_PAID_and_syncs_order_to_PAID() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.ONLINE, new BigDecimal("200000"));
            Payment p = payment(uuid(10), o, PaymentRecordStatus.INITIATED);
            when(orderRepository.findByOrderCode("ORD202604060001")).thenReturn(Optional.of(o));
            when(paymentRepository.findByOrderIdWithLock(uuid(1))).thenReturn(Optional.of(p));
            when(transactionRepository.findByProviderTxnId("TXN001")).thenReturn(Optional.empty());
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentMapper.toResponse(any())).thenReturn(mock(PaymentResponse.class));

            paymentService.processCallback(callback("ORD202604060001", "SUCCESS", "TXN001"));

            verify(paymentRepository).save(argThat(
                    saved -> saved.getStatus() == PaymentRecordStatus.PAID
                             && saved.getPaidAt() != null));
            verify(orderRepository).save(argThat(
                    saved -> saved.getPaymentStatus() == PaymentStatus.PAID));
        }

        @Test
        void FAILED_callback_sets_payment_FAILED_and_syncs_order_to_FAILED() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.ONLINE, new BigDecimal("200000"));
            Payment p = payment(uuid(10), o, PaymentRecordStatus.INITIATED);
            when(orderRepository.findByOrderCode("ORD202604060001")).thenReturn(Optional.of(o));
            when(paymentRepository.findByOrderIdWithLock(uuid(1))).thenReturn(Optional.of(p));
            when(transactionRepository.findByProviderTxnId("TXN001")).thenReturn(Optional.empty());
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentMapper.toResponse(any())).thenReturn(mock(PaymentResponse.class));

            paymentService.processCallback(callback("ORD202604060001", "FAILED", "TXN001"));

            verify(paymentRepository).save(argThat(
                    saved -> saved.getStatus() == PaymentRecordStatus.FAILED));
            verify(orderRepository).save(argThat(
                    saved -> saved.getPaymentStatus() == PaymentStatus.FAILED));
        }

        @Test
        void SUCCESS_callback_records_transaction_with_SUCCESS_status() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.ONLINE, new BigDecimal("200000"));
            Payment p = payment(uuid(10), o, PaymentRecordStatus.INITIATED);
            when(orderRepository.findByOrderCode("ORD202604060001")).thenReturn(Optional.of(o));
            when(paymentRepository.findByOrderIdWithLock(uuid(1))).thenReturn(Optional.of(p));
            when(transactionRepository.findByProviderTxnId("TXN001")).thenReturn(Optional.empty());
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentMapper.toResponse(any())).thenReturn(mock(PaymentResponse.class));

            paymentService.processCallback(callback("ORD202604060001", "SUCCESS", "TXN001"));

            verify(transactionRepository).save(argThat(txn ->
                    txn.getStatus() == TransactionStatus.SUCCESS
                    && "TXN001".equals(txn.getProviderTxnId())
                    && "CALLBACK".equals(txn.getReferenceType())));
        }

        @Test
        void FAILED_callback_records_transaction_with_FAILED_status() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.ONLINE, new BigDecimal("200000"));
            Payment p = payment(uuid(10), o, PaymentRecordStatus.INITIATED);
            when(orderRepository.findByOrderCode("ORD202604060001")).thenReturn(Optional.of(o));
            when(paymentRepository.findByOrderIdWithLock(uuid(1))).thenReturn(Optional.of(p));
            when(transactionRepository.findByProviderTxnId("TXN002")).thenReturn(Optional.empty());
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentMapper.toResponse(any())).thenReturn(mock(PaymentResponse.class));

            paymentService.processCallback(callback("ORD202604060001", "FAILED", "TXN002"));

            verify(transactionRepository).save(argThat(
                    txn -> txn.getStatus() == TransactionStatus.FAILED));
        }

        @Test
        void null_providerTxnId_skips_duplicate_check() {
            Customer cust = customer(uuid(1));
            Order o = order(uuid(1), cust, PaymentMethod.ONLINE, new BigDecimal("200000"));
            Payment p = payment(uuid(10), o, PaymentRecordStatus.INITIATED);
            when(orderRepository.findByOrderCode("ORD202604060001")).thenReturn(Optional.of(o));
            when(paymentRepository.findByOrderIdWithLock(uuid(1))).thenReturn(Optional.of(p));
            // providerTxnId is null — no transactionRepository.findByProviderTxnId call
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentMapper.toResponse(any())).thenReturn(mock(PaymentResponse.class));

            paymentService.processCallback(callback("ORD202604060001", "SUCCESS", null));

            verify(transactionRepository, never()).findByProviderTxnId(any());
            verify(paymentRepository).save(any());
        }
    }

    // ─── getPaymentForCustomer ─────────────────────────────────────────────────

    @Nested
    class GetPaymentForCustomer {

        @Test
        void throws_ORDER_NOT_FOUND_when_order_belongs_to_different_customer() {
            Customer cust = customer(uuid(1));
            Customer other = customer(uuid(2));
            Order o = order(uuid(1), other, PaymentMethod.ONLINE, new BigDecimal("200000"));
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(o));

            assertThatThrownBy(() ->
                    paymentService.getPaymentForCustomer(uuid(1), cust))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        }
    }
}
