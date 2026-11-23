package com.locnguyen.ecommerce.integration;

import com.locnguyen.ecommerce.domains.address.entity.Address;
import com.locnguyen.ecommerce.domains.address.repository.AddressRepository;
import com.locnguyen.ecommerce.domains.cart.dto.AddCartItemRequest;
import com.locnguyen.ecommerce.domains.cart.enums.CartStatus;
import com.locnguyen.ecommerce.domains.cart.repository.CartRepository;
import com.locnguyen.ecommerce.domains.cart.service.CartService;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.customer.repository.CustomerRepository;
import com.locnguyen.ecommerce.domains.inventory.entity.Inventory;
import com.locnguyen.ecommerce.domains.inventory.entity.Warehouse;
import com.locnguyen.ecommerce.domains.inventory.repository.InventoryRepository;
import com.locnguyen.ecommerce.domains.inventory.repository.InventoryReservationRepository;
import com.locnguyen.ecommerce.domains.inventory.service.InventoryScheduler;
import com.locnguyen.ecommerce.domains.inventory.service.InventoryService;
import com.locnguyen.ecommerce.domains.inventory.dto.ReserveStockRequest;
import com.locnguyen.ecommerce.domains.inventory.repository.WarehouseRepository;
import com.locnguyen.ecommerce.domains.order.dto.CreateOrderRequest;
import com.locnguyen.ecommerce.domains.order.dto.OrderFilter;
import com.locnguyen.ecommerce.domains.order.dto.OrderResponse;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.order.service.OrderService;
import com.locnguyen.ecommerce.domains.payment.dto.InitPaymentRequest;
import com.locnguyen.ecommerce.domains.payment.dto.PaymentCallbackRequest;
import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import com.locnguyen.ecommerce.domains.payment.enums.PaymentRecordStatus;
import com.locnguyen.ecommerce.domains.payment.enums.TransactionStatus;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentRepository;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentTransactionRepository;
import com.locnguyen.ecommerce.domains.payment.service.PaymentService;
import com.locnguyen.ecommerce.domains.product.entity.Product;
import com.locnguyen.ecommerce.domains.product.enums.ProductStatus;
import com.locnguyen.ecommerce.domains.product.repository.ProductRepository;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import com.locnguyen.ecommerce.domains.productvariant.repository.ProductVariantRepository;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.repository.UserRepository;
import com.locnguyen.ecommerce.domains.order.repository.OrderItemRepository;
import com.locnguyen.ecommerce.domains.promotion.entity.Promotion;
import com.locnguyen.ecommerce.domains.promotion.entity.Voucher;
import com.locnguyen.ecommerce.domains.promotion.enums.DiscountType;
import com.locnguyen.ecommerce.domains.promotion.repository.PromotionRepository;
import com.locnguyen.ecommerce.domains.promotion.repository.VoucherRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class MockPaymentCommerceLifecycleIntegrationTest {

    private static final int ORDER_QUANTITY = 2;
    private static final int INITIAL_ON_HAND = 10;

    @Container
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:11.3")
            .withDatabaseName("ecommerce_test")
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
    @Autowired private AddressRepository addressRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVariantRepository productVariantRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private InventoryReservationRepository reservationRepository;
    @Autowired private InventoryScheduler inventoryScheduler;
    @Autowired private InventoryService inventoryService;
    @Autowired private CartRepository cartRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired private CartService cartService;
    @Autowired private OrderService orderService;
    @Autowired private PaymentService paymentService;
    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager entityManager;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private VoucherRepository voucherRepository;

    @Test
    void checkoutVoucher_previewMatchesPersistedLineSnapshotAndPaymentAmount() {
        CommerceFixture fixture = createFixture();
        addProductToCart(fixture);

        Promotion promotion = new Promotion();
        promotion.setName("Checkout snapshot test");
        promotion.setDiscountType(DiscountType.PERCENTAGE);
        promotion.setDiscountValue(new BigDecimal("10"));
        promotion.setActive(true);
        promotion.setStartDate(java.time.LocalDateTime.now().minusDays(1));
        promotion.setEndDate(java.time.LocalDateTime.now().plusDays(1));
        promotion = promotionRepository.saveAndFlush(promotion);

        Voucher voucher = new Voucher();
        voucher.setCode("SNAP-" + UUID.randomUUID());
        voucher.setPromotion(promotion);
        voucher.setStartDate(promotion.getStartDate());
        voucher.setEndDate(promotion.getEndDate());
        voucher.setActive(true);
        voucherRepository.saveAndFlush(voucher);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setShippingAddressId(fixture.addressId());
        request.setPaymentMethod(PaymentMethod.ONLINE);
        request.setVoucherCode(voucher.getCode());

        var voucherQuote = orderService.quoteCartVoucher(fixture.customer(), voucher.getCode());
        var preview = orderService.previewOrder(fixture.customer(), request);
        var order = orderService.createOrder(fixture.customer(), request, UUID.randomUUID().toString());
        initiateMockPayment(order, fixture);
        entityManager.clear();

        assertThat(order.getDiscountAmount()).isEqualByComparingTo(voucherQuote.getDiscountAmount());
        assertThat(order.getDiscountAmount()).isEqualByComparingTo(preview.getDiscountAmount());
        assertThat(order.getTotalAmount()).isEqualByComparingTo(preview.getTotalAmount());
        assertThat(paymentRepository.findByOrderId(order.getId()).orElseThrow().getAmount())
                .isEqualByComparingTo(order.getTotalAmount());
        assertThat(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(order.getId())).singleElement()
                .satisfies(item -> {
                    assertThat(item.getQuantity()).isEqualTo(ORDER_QUANTITY);
                    assertThat(item.getVoucherEligible()).isTrue();
                    assertThat(item.getVoucherAllocationVersion()).isEqualTo(1);
                    assertThat(item.getVoucherDiscountAmount()).isEqualByComparingTo(order.getDiscountAmount());
                    assertThat(item.getNetLineAmount()).isEqualByComparingTo(
                            item.getLineTotal().subtract(order.getDiscountAmount()));
                });
    }

    @Test
    void mockPaymentCommerceLifecycle_reservesStockMarksPaymentPaidProcessesOrderAndShowsCustomerHistory()
            throws Exception {
        CommerceFixture fixture = createFixture();
        addProductToCart(fixture);

        OrderResponse createdOrder = checkout(fixture);
        assertThat(createdOrder.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(createdOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(cartRepository.findById(fixture.cartId()).orElseThrow().getStatus())
                .isEqualTo(CartStatus.CHECKED_OUT);
        assertInventory(fixture.inventoryId(), INITIAL_ON_HAND, ORDER_QUANTITY);

        InitPaymentRequest initRequest = new InitPaymentRequest();
        initRequest.setProvider("MOCK");
        var initiatedPayment = paymentService.initiateOnlinePayment(
                createdOrder.getId(), fixture.customer(), initRequest, UUID.randomUUID().toString());
        assertThat(initiatedPayment.getPaymentUrl()).contains("/api/v1/payments/mock/complete");

        Payment payment = paymentRepository.findByOrderId(createdOrder.getId()).orElseThrow();
        String providerTransactionId = "MOCK-" + payment.getPaymentCode();
        completeMockPayment(createdOrder.getOrderCode(), providerTransactionId);
        completeMockPayment(createdOrder.getOrderCode(), providerTransactionId);

        entityManager.clear();
        Payment paidPayment = paymentRepository.findByOrderId(createdOrder.getId()).orElseThrow();
        OrderResponse paidOrder = orderService.getOrderById(createdOrder.getId(), fixture.customer());
        assertThat(paidPayment.getStatus()).isEqualTo(PaymentRecordStatus.PAID);
        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(paidOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paymentTransactionRepository.findByPaymentIdOrderByCreatedAtDesc(paidPayment.getId()))
                .filteredOn(transaction -> transaction.getStatus() == TransactionStatus.SUCCESS)
                .hasSize(1);

        assertThat(orderService.confirmOrder(createdOrder.getId()).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(orderService.processOrder(createdOrder.getId()).getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertInventory(fixture.inventoryId(), INITIAL_ON_HAND, ORDER_QUANTITY);

        var orderHistory = orderService.getMyOrders(fixture.customer(), new OrderFilter(), PageRequest.of(0, 20));
        assertThat(orderHistory.getItems())
                .singleElement()
                .satisfies(order -> {
                    assertThat(order.getId()).isEqualTo(createdOrder.getId());
                    assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
                    assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
                });
    }

    @Test
    void concurrentSuccessCallbacks_recordOnlyOnePaymentTransition() throws Exception {
        CommerceFixture fixture = createFixture();
        addProductToCart(fixture);
        OrderResponse order = checkout(fixture);
        InitPaymentRequest initiate = new InitPaymentRequest();
        initiate.setProvider("MOCK");
        paymentService.initiateOnlinePayment(order.getId(), fixture.customer(), initiate,
                UUID.randomUUID().toString());
        PaymentCallbackRequest callback = new PaymentCallbackRequest();
        callback.setOrderCode(order.getOrderCode());
        callback.setStatus("SUCCESS");
        callback.setProvider("VNPAY");
        callback.setProviderTxnId("CONCURRENT-" + UUID.randomUUID());
        callback.setPayload("{\"event\":\"SUCCESS\"}");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> runCallback(ready, start, callback));
        CompletableFuture<Void> second = CompletableFuture.runAsync(() -> runCallback(ready, start, callback));
        ready.await();
        start.countDown();
        CompletableFuture.allOf(first, second).join();

        entityManager.clear();
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentRecordStatus.PAID);
        assertThat(paymentTransactionRepository.findByPaymentIdOrderByCreatedAtDesc(payment.getId()))
                .filteredOn(transaction -> transaction.getStatus() == TransactionStatus.SUCCESS)
                .hasSize(1);
    }

    @Test
    void expiredUnpaidOrderReleasesStockAndLateSuccessRequiresRefund() {
        CommerceFixture fixture = createFixture();
        addProductToCart(fixture);
        OrderResponse order = checkout(fixture);
        initiateMockPayment(order, fixture);
        var reservation = reservationRepository.findExpiredReservations(LocalDateTime.now().plusYears(1)).stream()
                .filter(row -> order.getOrderCode().equals(row.getReferenceId()))
                .findFirst().orElseThrow();
        reservation.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        reservationRepository.saveAndFlush(reservation);

        inventoryScheduler.releaseExpiredReservations();
        inventoryScheduler.releaseExpiredReservations();
        entityManager.clear();
        assertThat(orderService.getOrderById(order.getId(), fixture.customer()).getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(paymentRepository.findByOrderId(order.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentRecordStatus.CANCELLED);
        assertInventory(fixture.inventoryId(), INITIAL_ON_HAND, 0);

        Inventory stock = inventoryRepository.findById(fixture.inventoryId()).orElseThrow();
        ReserveStockRequest anotherOrder = new ReserveStockRequest();
        anotherOrder.setVariantId(fixture.variantId());
        anotherOrder.setWarehouseId(stock.getWarehouse().getId());
        anotherOrder.setQuantity(INITIAL_ON_HAND);
        anotherOrder.setReferenceType("ORDER");
        anotherOrder.setReferenceId("OTHER-ORDER-" + UUID.randomUUID());
        inventoryService.reserveStock(anotherOrder);

        PaymentCallbackRequest callback = new PaymentCallbackRequest();
        callback.setOrderCode(order.getOrderCode());
        callback.setStatus("SUCCESS");
        callback.setProvider("VNPAY");
        callback.setProviderTxnId("LATE-" + UUID.randomUUID());
        paymentService.processCallback(callback);

        entityManager.clear();
        assertThat(orderService.getOrderById(order.getId(), fixture.customer()).getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(paymentRepository.findByOrderId(order.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentRecordStatus.PAID);
        assertThat(paymentTransactionRepository.findByPaymentIdOrderByCreatedAtDesc(
                paymentRepository.findByOrderId(order.getId()).orElseThrow().getId()))
                .anySatisfy(transaction -> assertThat(transaction.getReferenceType())
                        .isEqualTo("LATE_SUCCESS_AFTER_CANCEL"));
        assertInventory(fixture.inventoryId(), INITIAL_ON_HAND, INITIAL_ON_HAND);
    }

    @Test
    void concurrentExpiryAndCallbackNeverFulfillWithoutStock() throws Exception {
        CommerceFixture fixture = createFixture();
        addProductToCart(fixture);
        OrderResponse order = checkout(fixture);
        initiateMockPayment(order, fixture);
        var reservation = reservationRepository.findExpiredReservations(LocalDateTime.now().plusYears(1)).stream()
                .filter(row -> order.getOrderCode().equals(row.getReferenceId()))
                .findFirst().orElseThrow();
        reservation.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        reservationRepository.saveAndFlush(reservation);

        PaymentCallbackRequest callback = new PaymentCallbackRequest();
        callback.setOrderCode(order.getOrderCode());
        callback.setStatus("SUCCESS");
        callback.setProvider("VNPAY");
        callback.setProviderTxnId("EXPIRY-RACE-" + UUID.randomUUID());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<Void> expiry = CompletableFuture.runAsync(() -> {
            ready.countDown();
            awaitStart(start);
            inventoryScheduler.releaseExpiredReservations();
        });
        CompletableFuture<Void> success = CompletableFuture.runAsync(() -> runCallback(ready, start, callback));
        ready.await();
        start.countDown();
        CompletableFuture.allOf(expiry, success).join();

        entityManager.clear();
        OrderResponse current = orderService.getOrderById(order.getId(), fixture.customer());
        assertThat(current.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(current.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertInventory(fixture.inventoryId(), INITIAL_ON_HAND, 0);
    }

    private void runCallback(CountDownLatch ready, CountDownLatch start,
                             PaymentCallbackRequest callback) {
        ready.countDown();
        awaitStart(start);
        paymentService.processCallback(callback);
    }

    private void awaitStart(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting concurrent callback", exception);
        }
    }

    private void initiateMockPayment(OrderResponse order, CommerceFixture fixture) {
        InitPaymentRequest request = new InitPaymentRequest();
        request.setProvider("MOCK");
        paymentService.initiateOnlinePayment(order.getId(), fixture.customer(), request,
                UUID.randomUUID().toString());
    }

    private CommerceFixture createFixture() {
        String suffix = UUID.randomUUID().toString();

        User user = new User();
        user.setEmail("lifecycle-" + suffix + "@example.test");
        user.setPasswordHash("not-used-by-this-integration-test");
        user.setFirstName("Lifecycle");
        user.setLastName("Customer");
        user = userRepository.saveAndFlush(user);

        Customer customer = customerRepository.saveAndFlush(new Customer(user));

        Address address = new Address();
        address.setCustomer(customer);
        address.setReceiverName("Lifecycle Customer");
        address.setPhoneNumber("0900000000");
        address.setStreetAddress("1 Test Street");
        address.setWard("Ward 1");
        address.setDistrict("District 1");
        address.setCity("Ho Chi Minh City");
        address = addressRepository.saveAndFlush(address);

        Product product = new Product();
        product.setName("Lifecycle Product " + suffix);
        product.setSlug("lifecycle-product-" + suffix);
        product.setStatus(ProductStatus.PUBLISHED);
        product = productRepository.saveAndFlush(product);

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku("LIFECYCLE-" + suffix);
        variant.setVariantName("Default");
        variant.setBasePrice(new BigDecimal("100000.00"));
        variant = productVariantRepository.saveAndFlush(variant);

        Warehouse warehouse = new Warehouse();
        warehouse.setName("Lifecycle Warehouse " + suffix);
        warehouse.setCode("LIFE-" + suffix.substring(0, 8));
        warehouse = warehouseRepository.saveAndFlush(warehouse);

        Inventory inventory = new Inventory();
        inventory.setVariant(variant);
        inventory.setWarehouse(warehouse);
        inventory.setOnHand(INITIAL_ON_HAND);
        inventory.setReserved(0);
        inventory = inventoryRepository.saveAndFlush(inventory);

        return new CommerceFixture(customer, address.getId(), variant.getId(), inventory.getId());
    }

    private void addProductToCart(CommerceFixture fixture) {
        AddCartItemRequest request = new AddCartItemRequest();
        request.setVariantId(fixture.variantId());
        request.setQuantity(ORDER_QUANTITY);
        cartService.addItem(fixture.customer(), request);
        entityManager.clear();
        fixture.setCartId(cartRepository.findByCustomerIdAndStatus(fixture.customer().getId(), CartStatus.ACTIVE)
                .orElseThrow()
                .getId());
    }

    private OrderResponse checkout(CommerceFixture fixture) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setShippingAddressId(fixture.addressId());
        request.setPaymentMethod(PaymentMethod.ONLINE);
        return orderService.createOrder(fixture.customer(), request, UUID.randomUUID().toString());
    }

    private void completeMockPayment(String orderCode, String providerTransactionId) throws Exception {
        mockMvc.perform(get("/api/v1/payments/mock/complete")
                        .param("orderCode", orderCode)
                        .param("providerTxnId", providerTransactionId)
                        .param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    private void assertInventory(UUID inventoryId, int onHand, int reserved) {
        entityManager.clear();
        Inventory inventory = inventoryRepository.findById(inventoryId).orElseThrow();
        assertThat(inventory.getOnHand()).isEqualTo(onHand);
        assertThat(inventory.getReserved()).isEqualTo(reserved);
        assertThat(inventory.getAvailable()).isEqualTo(onHand - reserved);
    }

    private static final class CommerceFixture {
        private final Customer customer;
        private final UUID addressId;
        private final UUID variantId;
        private final UUID inventoryId;
        private UUID cartId;

        private CommerceFixture(Customer customer, UUID addressId, UUID variantId, UUID inventoryId) {
            this.customer = customer;
            this.addressId = addressId;
            this.variantId = variantId;
            this.inventoryId = inventoryId;
        }

        private Customer customer() { return customer; }
        private UUID addressId() { return addressId; }
        private UUID variantId() { return variantId; }
        private UUID inventoryId() { return inventoryId; }
        private UUID cartId() { return cartId; }
        private void setCartId(UUID cartId) { this.cartId = cartId; }
    }
}
