package com.locnguyen.ecommerce.integration;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.customer.repository.CustomerRepository;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.payment.dto.RefundRequest;
import com.locnguyen.ecommerce.domains.payment.dto.RefundResponse;
import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import com.locnguyen.ecommerce.domains.payment.enums.PaymentRecordStatus;
import com.locnguyen.ecommerce.domains.payment.enums.RefundStatus;
import com.locnguyen.ecommerce.domains.payment.enums.TransactionStatus;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentRepository;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentRefundRepository;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentTransactionRepository;
import com.locnguyen.ecommerce.domains.payment.service.PaymentRefundService;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PaymentRefundConcurrencyIntegrationTest {

    @Container
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:11.3")
            .withDatabaseName("ecommerce_refund_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIADB::getJdbcUrl);
        registry.add("spring.datasource.username", MARIADB::getUsername);
        registry.add("spring.datasource.password", MARIADB::getPassword);
        registry.add("spring.datasource.driver-class-name", MARIADB::getDriverClassName);
    }

    @Autowired private UserRepository userRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentRefundRepository paymentRefundRepository;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired private PaymentRefundService paymentRefundService;
    @Autowired private EntityManager entityManager;

    @Test
    void concurrentRefundRequests_reservePendingAmountsAndRejectOverRefund() throws Exception {
        Payment payment = createPaidPayment();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicReference<ErrorCode> rejectionCode = new AtomicReference<>();

        CompletableFuture<Void> first = CompletableFuture.runAsync(
                () -> initiateRefund(payment.getId(), ready, start, accepted, rejected, rejectionCode));
        CompletableFuture<Void> second = CompletableFuture.runAsync(
                () -> initiateRefund(payment.getId(), ready, start, accepted, rejected, rejectionCode));
        ready.await();
        start.countDown();
        CompletableFuture.allOf(first, second).join();

        entityManager.clear();
        assertThat(accepted.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(1);
        assertThat(rejectionCode.get()).isEqualTo(ErrorCode.PAYMENT_REFUND_AMOUNT_EXCEEDED);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentRecordStatus.PAID);
    }

    @Test
    void concurrentCompletionRetries_updateRefundAndPaymentOnlyOnce() throws Exception {
        Payment payment = createPaidPayment();
        RefundResponse pending = paymentRefundService.initiateRefund(payment.getId(),
                new RefundRequest(new BigDecimal("100.00"), "full refund", null));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        CompletableFuture<Void> first = CompletableFuture.runAsync(
                () -> completeRefund(pending.refundCode(), ready, start));
        CompletableFuture<Void> second = CompletableFuture.runAsync(
                () -> completeRefund(pending.refundCode(), ready, start));
        ready.await();
        start.countDown();
        CompletableFuture.allOf(first, second).join();

        entityManager.clear();
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentRecordStatus.REFUNDED);
        assertThat(orderRepository.findById(payment.getOrder().getId()).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(paymentRefundRepository.findByPaymentIdOrderByCreatedAtDesc(payment.getId()))
                .singleElement()
                .satisfies(refund -> assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED));
        assertThat(paymentTransactionRepository.findByPaymentIdOrderByCreatedAtDesc(payment.getId()))
                .filteredOn(transaction -> transaction.getStatus() == TransactionStatus.REFUNDED)
                .hasSize(1);
    }

    private void completeRefund(String refundCode, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            paymentRefundService.completeRefund(refundCode, "PROVIDER-" + refundCode);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted before refund completion", exception);
        }
    }

    private void initiateRefund(UUID paymentId, CountDownLatch ready, CountDownLatch start,
                                AtomicInteger accepted, AtomicInteger rejected,
                                AtomicReference<ErrorCode> rejectionCode) {
        ready.countDown();
        try {
            start.await();
            paymentRefundService.initiateRefund(paymentId,
                    new RefundRequest(new BigDecimal("60.00"), "concurrency test", null));
            accepted.incrementAndGet();
        } catch (AppException exception) {
            rejected.incrementAndGet();
            rejectionCode.set(exception.getErrorCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted before refund request", exception);
        }
    }

    private Payment createPaidPayment() {
        String suffix = UUID.randomUUID().toString();
        User user = new User();
        user.setEmail("refund-" + suffix + "@example.test");
        user.setPasswordHash("not-used-by-this-integration-test");
        user = userRepository.saveAndFlush(user);

        Customer customer = customerRepository.saveAndFlush(new Customer(user));
        Order order = new Order();
        order.setCustomer(customer);
        order.setOrderCode("REFUND-" + suffix);
        order.setReceiverName("Refund Test");
        order.setReceiverPhone("0900000000");
        order.setShippingStreet("1 Test Street");
        order.setShippingWard("Test Ward");
        order.setShippingDistrict("Test District");
        order.setShippingCity("Test City");
        order.setSubTotal(new BigDecimal("100.00"));
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setPaymentMethod(PaymentMethod.ONLINE);
        order.setPaymentStatus(PaymentStatus.PAID);
        order = orderRepository.saveAndFlush(order);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentCode("PAY-" + suffix);
        payment.setMethod(PaymentMethod.ONLINE);
        payment.setStatus(PaymentRecordStatus.PAID);
        payment.setAmount(new BigDecimal("100.00"));
        payment = paymentRepository.saveAndFlush(payment);
        entityManager.clear();
        return payment;
    }
}
