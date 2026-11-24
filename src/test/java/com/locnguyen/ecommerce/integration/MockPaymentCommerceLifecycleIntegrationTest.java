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
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.notification.repository.NotificationRepository;
import com.locnguyen.ecommerce.domains.returns.dto.CreateReturnRequest;
import com.locnguyen.ecommerce.domains.returns.dto.ExchangeResponse;
import com.locnguyen.ecommerce.domains.returns.dto.ExchangeSelection;
import com.locnguyen.ecommerce.domains.returns.enums.ExchangeStatus;
import com.locnguyen.ecommerce.domains.returns.repository.ReturnExchangeRepository;
import com.locnguyen.ecommerce.domains.returns.service.ExchangeService;
import com.locnguyen.ecommerce.domains.returns.service.ReturnRefundService;
import com.locnguyen.ecommerce.domains.payment.enums.RefundStatus;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentRefundRepository;
import com.locnguyen.ecommerce.domains.payment.service.PaymentRefundService;
import com.locnguyen.ecommerce.domains.invoice.service.InvoiceService;
import com.locnguyen.ecommerce.domains.returns.dto.ReturnDecisionRequest;
import com.locnguyen.ecommerce.domains.returns.dto.ReturnHandlingRequest;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnPolicy;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnReason;
import com.locnguyen.ecommerce.domains.returns.repository.ReturnDecisionRepository;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnStatus;
import com.locnguyen.ecommerce.domains.returns.service.OrderReturnService;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderReturnService returnService;
    @Autowired private ExchangeService exchangeService;
    @Autowired private ReturnRefundService returnRefundService;
    @Autowired private PaymentRefundService paymentRefundService;
    @Autowired private PaymentRefundRepository paymentRefundRepository;

    @Test
    void returnRefund_reservesPartialAmountsWithoutLegacyCompletion() throws Exception {
        CommerceFixture fixture = createFixture();
        addProductToCart(fixture);
        Promotion promo = new Promotion();
        promo.setName("Partial return rounding");
        promo.setDiscountType(DiscountType.FIXED_AMOUNT);
        promo.setDiscountValue(BigDecimal.ONE);
        promo.setActive(true);
        promo.setStartDate(LocalDateTime.now().minusDays(1));
        promo.setEndDate(LocalDateTime.now().plusDays(1));
        promo = promotionRepository.saveAndFlush(promo);
        Voucher voucher = new Voucher();
        voucher.setCode("RETURN-" + UUID.randomUUID());
        voucher.setPromotion(promo);
        voucher.setStartDate(promo.getStartDate());
        voucher.setEndDate(promo.getEndDate());
        voucher.setActive(true);
        voucherRepository.saveAndFlush(voucher);

        CreateOrderRequest checkout = new CreateOrderRequest();
        checkout.setShippingAddressId(fixture.addressId());
        checkout.setPaymentMethod(PaymentMethod.ONLINE);
        checkout.setVoucherCode(voucher.getCode());
        var order = orderService.createOrder(fixture.customer(), checkout, UUID.randomUUID().toString());
        initiateMockPayment(order, fixture);
        completeMockPayment(order.getOrderCode(), "RETURN-" + UUID.randomUUID());
        var original = orderRepository.findById(order.getId()).orElseThrow();
        original.setStatus(OrderStatus.DELIVERED);
        orderRepository.saveAndFlush(original);
        var item = orderItemRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()).get(0);

        var first = inspectOneDefectiveReturn(fixture, order, item.getId(), "part-1");
        var second = inspectOneDefectiveReturn(fixture, order, item.getId(), "part-2");
        var refundOne = returnRefundService.prepare(order.getId(), first.id());
        var refundTwo = returnRefundService.prepare(order.getId(), second.id());
        assertThat(refundOne.amount()).isEqualByComparingTo("99999");
        assertThat(refundTwo.amount()).isEqualByComparingTo("100000");
        assertThat(refundOne.status()).isEqualTo(RefundStatus.PENDING);
        assertThat(returnRefundService.prepare(order.getId(), first.id()).refundCode())
                .isEqualTo(refundOne.refundCode());
        var ledger = paymentRefundRepository.findByReturnId(first.id()).orElseThrow();
        assertThat(ledger.getReturnShippingAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ledger.getReturnLineAllocations()).singleElement().satisfies(line -> {
            assertThat(line.orderItemId()).isEqualTo(item.getId());
            assertThat(line.quantity()).isEqualTo(1);
            assertThat(line.amount()).isEqualByComparingTo(refundOne.amount());
        });
        assertThatThrownBy(() -> paymentRefundService.completeRefund(refundOne.refundCode(), "caller-supplied"))
                .isInstanceOf(AppException.class).extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_REFUND_INVALID_STATUS);
        assertThat(paymentRepository.findByOrderId(order.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentRecordStatus.PAID);
    }

    private com.locnguyen.ecommerce.domains.returns.dto.ReturnResponse inspectOneDefectiveReturn(
            CommerceFixture fixture, OrderResponse order, UUID itemId, String key) {
        String actor = "returns-admin@example.test";
        var entry = returnService.create(order.getId(), fixture.customer(), key,
                new CreateReturnRequest(ReturnReason.DEFECTIVE, "Faulty item",
                        List.of(new CreateReturnRequest.Item(itemId, 1))));
        returnService.decide(order.getId(), entry.id(), "verify", actor, ReturnStatus.REQUESTED,
                new ReturnDecisionRequest("Delivery receipt", LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(1)));
        returnService.decide(order.getId(), entry.id(), "approve", actor, ReturnStatus.APPROVED,
                new ReturnDecisionRequest("Complaint accepted", null));
        returnService.handle(order.getId(), entry.id(), "receive", actor, ReturnStatus.RECEIVED,
                new ReturnHandlingRequest("Received", null, null, null, null, null,
                        List.of(new ReturnHandlingRequest.Line(itemId, 1, 0, 0))));
        return returnService.handle(order.getId(), entry.id(), "inspect", actor, ReturnStatus.INSPECTED,
                new ReturnHandlingRequest("Fault confirmed", null, null, null, null, null,
                        List.of(new ReturnHandlingRequest.Line(itemId, 1, 0, 1))));
    }
    @Autowired private InvoiceService invoiceService;
    @Autowired private ReturnExchangeRepository exchangeRepository;

    @Test
    void exchangeReservation_usesHistoricalPriceAndReleasesExpiredStock() {
        CommerceFixture fixture = createFixture();
        var purchased = productVariantRepository.findById(fixture.variantId()).orElseThrow();
        var warehouse = inventoryRepository.findById(fixture.inventoryId()).orElseThrow().getWarehouse();
        var samePrice = saveSibling(purchased, "100000");
        var differentPrice = saveSibling(purchased, "120000");
        var emptyStock = saveSibling(purchased, "100000");
        Inventory alternateInventory = new Inventory();
        alternateInventory.setVariant(samePrice);
        alternateInventory.setWarehouse(warehouse);
        alternateInventory.setOnHand(1);
        alternateInventory = inventoryRepository.saveAndFlush(alternateInventory);

        addProductToCart(fixture);
        OrderResponse order = checkout(fixture);
        var storedOrder = orderRepository.findById(order.getId()).orElseThrow();
        storedOrder.setStatus(OrderStatus.DELIVERED);
        orderRepository.saveAndFlush(storedOrder);
        samePrice.setBasePrice(new BigDecimal("140000"));
        productVariantRepository.saveAndFlush(samePrice);
        var noHistory = saveSibling(purchased, "100000");
        var itemId = orderItemRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()).get(0).getId();
        var request = new CreateReturnRequest(ReturnReason.DEFECTIVE, "Damaged seam",
                List.of(new CreateReturnRequest.Item(itemId, 1)));
        var entry = returnService.create(order.getId(), fixture.customer(), "return", request);
        String actor = "returns-admin@example.test";
        returnService.decide(order.getId(), entry.id(), "verify", actor, ReturnStatus.REQUESTED,
                new ReturnDecisionRequest("Delivery receipt", LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(1)));
        returnService.decide(order.getId(), entry.id(), "approve", actor, ReturnStatus.APPROVED,
                new ReturnDecisionRequest("Complaint accepted", null));

        var unknown = new ExchangeSelection(itemId, noHistory.getId(), warehouse.getId(), 1, null);
        assertThat(exchangeService.reserve(order.getId(), entry.id(), "unknown", actor, unknown).status())
                .isEqualTo(ExchangeStatus.PRICE_REVIEW_REQUIRED);
        var unequal = new ExchangeSelection(itemId, differentPrice.getId(), warehouse.getId(), 1, null);
        assertThat(exchangeService.reserve(order.getId(), entry.id(), "unequal", actor, unequal).status())
                .isEqualTo(ExchangeStatus.DIFFERENT_PRICE);
        var unavailable = new ExchangeSelection(itemId, emptyStock.getId(), warehouse.getId(), 1, null);
        assertThat(exchangeService.reserve(order.getId(), entry.id(), "empty", actor, unavailable).status())
                .isEqualTo(ExchangeStatus.WAITING_CUSTOMER_CHOICE);

        var selection = new ExchangeSelection(itemId, samePrice.getId(), warehouse.getId(), 1, null);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var racingRequests = java.util.stream.Stream.of("hold-a", "hold-b")
                .map(key -> CompletableFuture.supplyAsync(() -> {
                    ready.countDown();
                    awaitStart(start);
                    try {
                        return new ExchangeRaceResult(key,
                                exchangeService.reserve(order.getId(), entry.id(), key, actor, selection), null);
                    } catch (AppException exception) {
                        return new ExchangeRaceResult(key, null, exception.getErrorCode());
                    }
                })).toList();
        awaitStart(ready);
        start.countDown();
        var results = racingRequests.stream().map(CompletableFuture::join).toList();
        assertThat(results).filteredOn(result -> result.error() == ErrorCode.RETURN_QUANTITY_UNAVAILABLE)
                .hasSize(1);
        var winner = results.stream().filter(result -> result.exchange() != null).findFirst().orElseThrow();
        var held = winner.exchange();
        assertThat(held.status()).isEqualTo(ExchangeStatus.RESERVED);
        assertThat(held.expiresAt()).isBetween(LocalDateTime.now(java.time.ZoneOffset.UTC).plusHours(47),
                LocalDateTime.now(java.time.ZoneOffset.UTC).plusHours(48));
        assertThat(exchangeService.reserve(order.getId(), entry.id(), winner.key(), actor, selection)).isEqualTo(held);
        assertInventory(alternateInventory.getId(), 1, 1);

        LocalDateTime expiredAt = LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(1);
        var storedExchange = exchangeRepository.findById(held.id()).orElseThrow();
        storedExchange.setExpiresAt(expiredAt);
        exchangeRepository.saveAndFlush(storedExchange);
        var stockHold = reservationRepository.findByVariantIdAndStatus(samePrice.getId(),
                com.locnguyen.ecommerce.domains.inventory.enums.ReservationStatus.PENDING).get(0);
        stockHold.setExpiresAt(expiredAt);
        reservationRepository.saveAndFlush(stockHold);
        inventoryService.releaseExpiredReservations();
        assertInventory(alternateInventory.getId(), 1, 0);
        assertThat(exchangeService.reserve(order.getId(), entry.id(), winner.key(), actor, selection).status())
                .isEqualTo(ExchangeStatus.HOLD_EXPIRED);

        var retry = exchangeService.reserve(order.getId(), entry.id(), "new-hold", actor, selection);
        assertThat(retry.status()).isEqualTo(ExchangeStatus.RESERVED);
        assertThat(exchangeService.cancel(order.getId(), entry.id(), retry.id(), actor).status())
                .isEqualTo(ExchangeStatus.CANCELLED);
        exchangeService.cancel(order.getId(), entry.id(), retry.id(), actor);
        assertInventory(alternateInventory.getId(), 1, 0);

        var nextHold = exchangeService.reserve(order.getId(), entry.id(), "ready-hold", actor, selection);
        assertThatThrownBy(() -> exchangeService.createReplacement(order.getId(), entry.id(), nextHold.id()))
                .isInstanceOf(AppException.class).extracting("errorCode").isEqualTo(ErrorCode.RETURN_DECISION_INVALID);
        returnService.handle(order.getId(), entry.id(), "receive", actor, ReturnStatus.RECEIVED,
                new ReturnHandlingRequest("Received", null, null, null, null, null,
                        List.of(new ReturnHandlingRequest.Line(itemId, 1, 0, 0))));
        returnService.handle(order.getId(), entry.id(), "inspect", actor, ReturnStatus.INSPECTED,
                new ReturnHandlingRequest("Passed inspection", null, null, null, null, warehouse.getId(),
                        List.of(new ReturnHandlingRequest.Line(itemId, 1, 1, 0))));
        assertThat(exchangeService.createReplacement(order.getId(), entry.id(), held.id()).status())
                .isEqualTo(ExchangeStatus.HOLD_EXPIRED);
        var replacement = exchangeService.createReplacement(order.getId(), entry.id(), nextHold.id());
        assertThat(replacement.status()).isEqualTo(ExchangeStatus.REPLACEMENT_CREATED);
        assertThat(exchangeService.createReplacement(order.getId(), entry.id(), nextHold.id())).isEqualTo(replacement);
        var child = orderService.getOrderById(replacement.replacementOrderId(), fixture.customer());
        assertThat(child.getReplacementForOrderId()).isEqualTo(order.getId());
        assertThat(child.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(child.getPaymentMethod()).isEqualTo(PaymentMethod.EXCHANGE);
        assertThat(child.getPaymentStatus()).isEqualTo(PaymentStatus.NOT_REQUIRED);
        assertThat(paymentRepository.findByOrderId(child.getId())).isEmpty();
        assertThatThrownBy(() -> invoiceService.generateInvoice(child.getId())).isInstanceOf(AppException.class);
        assertThat(orderService.processOrder(child.getId()).getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertInventory(alternateInventory.getId(), 1, 1);
    }

    private record ExchangeRaceResult(String key, ExchangeResponse exchange, ErrorCode error) {}

    private ProductVariant saveSibling(ProductVariant purchased, String basePrice) {
        ProductVariant sibling = new ProductVariant();
        sibling.setProduct(purchased.getProduct());
        sibling.setSku("EXCHANGE-" + UUID.randomUUID());
        sibling.setVariantName("Alternate");
        sibling.setBasePrice(new BigDecimal(basePrice));
        return productVariantRepository.saveAndFlush(sibling);
    }
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ReturnDecisionRepository returnDecisionRepository;

    @Test
    void returnIntake_checksOwnershipAndSerializesCompetingClaims() {
        CommerceFixture fixture = createFixture();
        addProductToCart(fixture);
        OrderResponse order = checkout(fixture);
        var storedOrder = orderRepository.findById(order.getId()).orElseThrow();
        storedOrder.setStatus(OrderStatus.DELIVERED);
        orderRepository.saveAndFlush(storedOrder);
        var itemId = orderItemRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()).get(0).getId();
        var request = new CreateReturnRequest(ReturnReason.DEFECTIVE, "Damaged seam",
                List.of(new CreateReturnRequest.Item(itemId, 1)));

        assertThatThrownBy(() -> returnService.create(order.getId(), createFixture().customer(), "owner", request))
                .isInstanceOf(AppException.class).extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        var first = returnService.create(order.getId(), fixture.customer(), "first", request);
        assertThat(first.status()).isEqualTo(ReturnStatus.PENDING_DELIVERY_VERIFICATION);
        var page = org.springframework.data.domain.PageRequest.of(0, 1);
        assertThat(returnService.listForAdmin(first.status(), order.getId(), page).getContent())
                .singleElement().satisfies(entry -> {
                    assertThat(entry.id()).isEqualTo(first.id());
                    assertThat(entry.items()).containsExactlyElementsOf(request.items());
                });
        assertThat(returnService.listForAdmin(ReturnStatus.APPROVED, order.getId(), page)).isEmpty();
        assertThat(returnService.listForAdmin(null, UUID.randomUUID(), page)).isEmpty();
        var discretionary = new CreateReturnRequest(ReturnReason.CHANGED_MIND, "Changed my mind",
                request.items(), true, true);
        assertThatThrownBy(() -> returnService.create(order.getId(), fixture.customer(), "policy", discretionary))
                .isInstanceOf(AppException.class).extracting("errorCode").isEqualTo(ErrorCode.RETURN_NOT_ELIGIBLE);
        assertThat(returnService.create(order.getId(), fixture.customer(), "first", request).id())
                .isEqualTo(first.id());
        var changed = new CreateReturnRequest(ReturnReason.WRONG_ITEM, request.note(), request.items());
        assertThatThrownBy(() -> returnService.create(order.getId(), fixture.customer(), "first", changed))
                .isInstanceOf(AppException.class).extracting("errorCode").isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);

        var start = new CountDownLatch(1);
        var ready = new CountDownLatch(2);
        var second = CompletableFuture.supplyAsync(() -> submitCompetingReturn(ready, start, order, fixture, "second", request));
        var third = CompletableFuture.supplyAsync(() -> submitCompetingReturn(ready, start, order, fixture, "third", request));
        awaitStart(ready);
        start.countDown();
        assertThat(List.of(second.join(), third.join())).containsExactlyInAnyOrder("CREATED", "RETURN_QUANTITY_UNAVAILABLE");

        String actor = "returns-admin@example.test";
        var decision = new ReturnDecisionRequest("Verified against delivery receipt",
                LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(1).withNano(0));
        var verified = returnService.decide(order.getId(), first.id(), "verify", actor, ReturnStatus.REQUESTED, decision);
        assertThat(verified.status()).isEqualTo(ReturnStatus.REQUESTED);
        assertThat(returnService.decide(order.getId(), first.id(), "verify", actor, ReturnStatus.REQUESTED, decision))
                .isEqualTo(verified);
        var audit = returnDecisionRepository.findByReturnIdAndRequestKey(first.id(), "verify").orElseThrow();
        assertThat(audit.getActor()).isEqualTo(actor);
        assertThat(audit.getReason()).isEqualTo(decision.reason());
        assertThat(audit.getVerifiedDeliveredAt()).isEqualTo(decision.deliveredAt());
        assertThat(audit.getPreviousStatus()).isEqualTo(ReturnStatus.PENDING_DELIVERY_VERIFICATION);

        var approval = new ReturnDecisionRequest("Approve customer complaint", null);
        assertThat(returnService.decide(order.getId(), first.id(), "approve", actor, ReturnStatus.APPROVED, approval)
                .status()).isEqualTo(ReturnStatus.APPROVED);
        assertThatThrownBy(() -> returnService.decide(
                order.getId(), first.id(), "approve", actor, ReturnStatus.REJECTED, approval))
                .isInstanceOf(AppException.class).extracting("errorCode").isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        var receipt = new ReturnHandlingRequest("Parcel received", "Example Carrier", "TRACK-1",
                LocalDateTime.now(java.time.ZoneOffset.UTC).withNano(0), "receipt-1", null,
                List.of(new ReturnHandlingRequest.Line(itemId, 1, 0, 0)));
        assertThat(returnService.handle(order.getId(), first.id(), "receive", actor, ReturnStatus.RECEIVED, receipt)
                .status()).isEqualTo(ReturnStatus.RECEIVED);

        var invalidWarehouse = new ReturnHandlingRequest("Restock inspected item", null, null, null, "inspection-1",
                UUID.randomUUID(), List.of(new ReturnHandlingRequest.Line(itemId, 1, 1, 0)));
        assertThatThrownBy(() -> returnService.handle(
                order.getId(), first.id(), "inspect", actor, ReturnStatus.INSPECTED, invalidWarehouse))
                .isInstanceOf(AppException.class);
        assertInventory(fixture.inventoryId(), INITIAL_ON_HAND, ORDER_QUANTITY);

        UUID warehouseId = inventoryRepository.findById(fixture.inventoryId()).orElseThrow().getWarehouse().getId();
        var inspection = new ReturnHandlingRequest("Restock inspected item", null, null, null, "inspection-1",
                warehouseId, List.of(new ReturnHandlingRequest.Line(itemId, 1, 1, 0)));
        var inspected = returnService.handle(order.getId(), first.id(), "inspect", actor, ReturnStatus.INSPECTED, inspection);
        assertThat(inspected.status()).isEqualTo(ReturnStatus.INSPECTED);
        assertThat(returnService.handle(order.getId(), first.id(), "inspect", actor, ReturnStatus.INSPECTED, inspection))
                .isEqualTo(inspected);
        assertInventory(fixture.inventoryId(), INITIAL_ON_HAND + 1, ORDER_QUANTITY);
        assertThat(notificationRepository.findByCustomerIdOrderByCreatedAtDesc(
                fixture.customer().getId(), PageRequest.of(0, 100)).getContent())
                .filteredOn(notification -> first.id().equals(notification.getReferenceId())).hasSize(5);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    private String submitCompetingReturn(CountDownLatch ready, CountDownLatch start, OrderResponse order,
                                         CommerceFixture fixture, String key, CreateReturnRequest request) {
        ready.countDown();
        awaitStart(start);
        try {
            returnService.create(order.getId(), fixture.customer(), key, request);
            return "CREATED";
        } catch (AppException exception) {
            return exception.getErrorCode().name();
        }
    }

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
        assertThat(preview.getReturnPolicies()).singleElement().satisfies(line ->
                assertThat(line.policy()).isEqualTo(ReturnPolicy.REVIEW_REQUIRED));
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
                    assertThat(item.getReturnPolicySnapshot()).isEqualTo(ReturnPolicy.REVIEW_REQUIRED);
                    assertThat(item.getExchangeBasePrices().get(fixture.variantId()))
                            .isEqualByComparingTo("100000");
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
