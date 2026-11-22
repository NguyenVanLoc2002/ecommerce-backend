package com.locnguyen.ecommerce.integration;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.carrier.entity.Carrier;
import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierConfigRepository;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierRepository;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.customer.repository.CustomerRepository;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.order.service.OrderHoldService;
import com.locnguyen.ecommerce.domains.shipment.entity.CarrierWebhookLog;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import com.locnguyen.ecommerce.domains.shipment.repository.CarrierWebhookLogRepository;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentEventRepository;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentRepository;
import com.locnguyen.ecommerce.domains.shipment.service.ShipmentProviderUpdate;
import com.locnguyen.ecommerce.domains.shipment.service.ShipmentService;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.repository.UserRepository;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.AhamoveConfigResolver;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.AhamoveResolvedConfig;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.AhamoveWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Exercises committed service transactions and genuine AhaMove mapping against MariaDB. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ShipmentWebhookReconciliationIntegrationTest {

    @Container
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:11.3")
            .withDatabaseName("ecommerce_shipment_reconciliation_test")
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
    @Autowired private ShipmentRepository shipmentRepository;
    @Autowired private ShipmentEventRepository eventRepository;
    @Autowired private CarrierRepository carrierRepository;
    @Autowired private CarrierConfigRepository configRepository;
    @Autowired private CarrierWebhookLogRepository logRepository;
    @Autowired private ShipmentService shipmentService;
    @Autowired private AhamoveWebhookService webhookService;
    @MockBean private OrderHoldService orderHoldService;
    @MockBean private AhamoveConfigResolver configResolver;

    @BeforeEach
    void configureAuthenticatedWebhook() {
        when(configResolver.resolve(any())).thenReturn(new AhamoveResolvedConfig(
                "https://example.test", "test-api-key", "0900000000", "Test carrier",
                "test-webhook-token", "Test pickup", "Test pickup", "Test sender", "0900000000",
                null, null, "BIKE", "CASH", List.of()));
        Carrier carrier = new Carrier();
        carrier.setCode("TEST-" + UUID.randomUUID());
        carrier.setName("Test AhaMove");
        carrier.setProviderType(CarrierProviderType.AHAMOVE);
        carrier = carrierRepository.saveAndFlush(carrier);
        CarrierConfig config = new CarrierConfig();
        config.setCarrier(carrier);
        configRepository.saveAndFlush(config);
    }

    @Test
    void failedProcessingRetry_reusesDurableLogAndCommitsOnlyOneMilestone() {
        Shipment shipment = createShipment(PaymentMethod.ONLINE, PaymentStatus.PENDING);
        String callback = pickupPayload(shipment);

        assertThatThrownBy(() -> receive(callback)).isInstanceOf(AppException.class);
        CarrierWebhookLog pending = webhookLog(shipment);
        assertThat(pending.isProcessed()).isFalse();
        assertThat(shipmentRepository.findById(shipment.getId()).orElseThrow().getStatus())
                .isEqualTo(ShipmentStatus.PENDING);
        assertThat(eventRepository.findByShipmentIdOrderByEventTimeAsc(shipment.getId())).isEmpty();

        confirmOnlinePayment(shipment);
        receive(callback);
        receive(callback);

        assertThat(webhookLog(shipment).getId()).isEqualTo(pending.getId());
        assertThat(webhookLog(shipment).isProcessed()).isTrue();
        assertThat(shipmentRepository.findById(shipment.getId()).orElseThrow().getStatus())
                .isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(orderRepository.findById(shipment.getOrder().getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.SHIPPED);
        assertThat(eventRepository.findByShipmentIdOrderByEventTimeAsc(shipment.getId())).hasSize(1);
    }

    @Test
    void concurrentPendingReplays_serializeLogAndPersistOneEvent() throws Exception {
        Shipment shipment = createShipment(PaymentMethod.ONLINE, PaymentStatus.PENDING);
        String callback = pickupPayload(shipment);
        assertThatThrownBy(() -> receive(callback)).isInstanceOf(AppException.class);
        confirmOnlinePayment(shipment);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> replay(callback, ready, start));
        CompletableFuture<Void> second = CompletableFuture.runAsync(() -> replay(callback, ready, start));
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        CompletableFuture.allOf(first, second).get(30, TimeUnit.SECONDS);

        assertThat(webhookLog(shipment).isProcessed()).isTrue();
        assertThat(eventRepository.findByShipmentIdOrderByEventTimeAsc(shipment.getId())).hasSize(1);
        assertThat(shipmentRepository.findById(shipment.getId()).orElseThrow().getStatus())
                .isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(orderRepository.findById(shipment.getOrder().getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void concurrentFirstDeliveries_createOneLogAndPersistOneEvent() throws Exception {
        Shipment shipment = createShipment(PaymentMethod.COD, PaymentStatus.PENDING);
        String callback = pickupPayload(shipment);
        assertThat(logRepository.findAll()).noneMatch(log ->
                shipment.getCarrierShipmentId().equals(log.getProviderOrderId()));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> replay(callback, ready, start));
        CompletableFuture<Void> second = CompletableFuture.runAsync(() -> replay(callback, ready, start));
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        CompletableFuture.allOf(first, second).get(30, TimeUnit.SECONDS);

        assertThat(webhookLog(shipment).isProcessed()).isTrue();
        assertThat(eventRepository.findByShipmentIdOrderByEventTimeAsc(shipment.getId())).hasSize(1);
        assertThat(shipmentRepository.findById(shipment.getId()).orElseThrow().getStatus())
                .isEqualTo(ShipmentStatus.IN_TRANSIT);
        Order order = orderRepository.findById(shipment.getOrder().getId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void genuineWebhookMilestones_advanceOrderOnlyAfterPickupAndDoNotCollectCod() {
        Shipment shipment = createShipment(PaymentMethod.COD, PaymentStatus.PENDING);
        receive(milestonePayload(shipment, "ACCEPTED", "", "PENDING"));
        assertThat(shipmentRepository.findById(shipment.getId()).orElseThrow().getStatus())
                .isEqualTo(ShipmentStatus.PICKING);
        assertThat(orderRepository.findById(shipment.getOrder().getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PROCESSING);

        receive(milestonePayload(shipment, "IN PROCESS", "COMPLETING", "PENDING"));
        assertThat(shipmentRepository.findById(shipment.getId()).orElseThrow().getStatus())
                .isEqualTo(ShipmentStatus.OUT_FOR_DELIVERY);
        assertThat(orderRepository.findById(shipment.getOrder().getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.SHIPPED);

        receive(milestonePayload(shipment, "COMPLETED", "", "COMPLETED"));
        var response = shipmentService.getById(shipment.getId());
        assertThat(response.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(response.getDeliveredAt()).isNotNull();
        assertThat(response.getAttention()).isNull();
        Order order = orderRepository.findById(shipment.getOrder().getId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(eventRepository.findByShipmentIdOrderByEventTimeAsc(shipment.getId()))
                .extracting(event -> event.getStatus())
                .containsExactly(ShipmentStatus.PICKING, ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.DELIVERED);
    }

    @Test
    void persistedCancellation_unknownAndSameMappedStatusDoNotReplaceAppliedException() {
        Shipment shipment = createShipment(PaymentMethod.COD, PaymentStatus.PENDING);
        LocalDateTime cancelledAt = LocalDateTime.of(2026, 11, 20, 12, 0);
        shipmentService.applyProviderUpdate(ShipmentProviderUpdate.builder()
                .shipmentId(shipment.getId()).status(ShipmentStatus.FAILED).providerStatus("CANCELLED")
                .description("Carrier cancelled").eventTime(cancelledAt).build());
        shipmentService.applyProviderUpdate(ShipmentProviderUpdate.builder()
                .shipmentId(shipment.getId()).providerStatus("NEW_UNKNOWN_STATUS")
                .eventTime(cancelledAt.plusMinutes(1)).build());
        shipmentService.applyProviderUpdate(ShipmentProviderUpdate.builder()
                .shipmentId(shipment.getId()).status(ShipmentStatus.FAILED).providerStatus("FAILED")
                .description("Earlier delivery failure").eventTime(cancelledAt.minusMinutes(1)).build());

        var response = shipmentService.getById(shipment.getId());
        assertThat(response.getStatus()).isEqualTo(ShipmentStatus.FAILED);
        assertThat(response.getProviderStatus()).isEqualTo("CANCELLED");
        assertThat(response.getAttention()).isEqualTo("CARRIER_CANCELLED");
        Customer owner = orderRepository.findAdminByIdWithCurrentCustomer(shipment.getOrder().getId())
                .orElseThrow().getCustomer();
        var customerResponse = shipmentService.getShipmentForCustomer(
                shipment.getOrder().getId(), owner);
        assertThat(customerResponse.getAttention()).isEqualTo("CARRIER_CANCELLED");
        Shipment foreignShipment = createShipment(PaymentMethod.COD, PaymentStatus.PENDING);
        Customer foreignCustomer = orderRepository.findAdminByIdWithCurrentCustomer(
                foreignShipment.getOrder().getId()).orElseThrow().getCustomer();
        assertThatThrownBy(() -> shipmentService.getShipmentForCustomer(
                shipment.getOrder().getId(), foreignCustomer))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        assertThat(eventRepository.findByShipmentIdOrderByEventTimeAsc(shipment.getId())).hasSize(2);
        Order order = orderRepository.findById(shipment.getOrder().getId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    private void replay(String callback, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Replay start timed out");
            receive(callback);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted before webhook replay", exception);
        }
    }

    private void receive(String callback) {
        webhookService.receiveWebhook(callback, Map.of("X-Webhook-Token", "test-webhook-token"));
    }

    private CarrierWebhookLog webhookLog(Shipment shipment) {
        return logRepository.findAll().stream()
                .filter(log -> shipment.getCarrierShipmentId().equals(log.getProviderOrderId()))
                .reduce((first, second) -> { throw new AssertionError("Duplicate webhook log"); })
                .orElseThrow();
    }

    private void confirmOnlinePayment(Shipment shipment) {
        Order order = orderRepository.findById(shipment.getOrder().getId()).orElseThrow();
        order.setPaymentStatus(PaymentStatus.PAID);
        orderRepository.saveAndFlush(order);
    }

    private String pickupPayload(Shipment shipment) {
        return """
                {"_id":"%s","status":"IN PROCESS","pickup_time":1795176000,
                 "path":[{"short_address":"Test pickup"},{"short_address":"Test dropoff"}]}
                """.formatted(shipment.getCarrierShipmentId());
    }

    private String milestonePayload(Shipment shipment, String status, String subStatus, String dropoffStatus) {
        return """
                {"_id":"%s","status":"%s","sub_status":"%s",
                 "accept_time":1795175940,"pickup_time":1795176000,"complete_time":1795176300,
                 "path":[{"short_address":"Test pickup"},
                         {"short_address":"Test dropoff","status":"%s","complete_time":1795176300}]}
                """.formatted(shipment.getCarrierShipmentId(), status, subStatus, dropoffStatus);
    }

    private Shipment createShipment(PaymentMethod paymentMethod, PaymentStatus paymentStatus) {
        String suffix = UUID.randomUUID().toString();
        User user = new User();
        user.setEmail("shipment-" + suffix + "@example.test");
        user.setPasswordHash("not-used-by-this-integration-test");
        user = userRepository.saveAndFlush(user);
        Customer customer = customerRepository.saveAndFlush(new Customer(user));
        Order order = new Order();
        order.setCustomer(customer);
        order.setOrderCode("SHIP-67-" + suffix);
        order.setStatus(OrderStatus.PROCESSING);
        order.setReceiverName("Shipment test");
        order.setReceiverPhone("0900000000");
        order.setShippingStreet("1 Test Street");
        order.setShippingWard("Test Ward");
        order.setShippingDistrict("Test District");
        order.setShippingCity("Test City");
        order.setSubTotal(new BigDecimal("100.00"));
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setPaymentMethod(paymentMethod);
        order.setPaymentStatus(paymentStatus);
        order = orderRepository.saveAndFlush(order);
        Shipment shipment = new Shipment();
        shipment.setOrder(order);
        shipment.setShipmentCode("SHIP-67-" + suffix);
        shipment.setCarrier("Test AhaMove");
        shipment.setCarrierShipmentId("AHA-67-" + suffix);
        return shipmentRepository.saveAndFlush(shipment);
    }
}
