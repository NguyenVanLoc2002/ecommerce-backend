package com.locnguyen.ecommerce.domains.order.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.address.entity.Address;
import com.locnguyen.ecommerce.domains.address.repository.AddressRepository;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.carrier.dto.CheckoutCarrierQuoteResponse;
import com.locnguyen.ecommerce.domains.carrier.dto.DeliveryEstimateResponse;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.service.CarrierCheckoutService;
import com.locnguyen.ecommerce.domains.cart.entity.Cart;
import com.locnguyen.ecommerce.domains.cart.entity.CartItem;
import com.locnguyen.ecommerce.domains.cart.enums.CartStatus;
import com.locnguyen.ecommerce.domains.cart.repository.CartItemRepository;
import com.locnguyen.ecommerce.domains.cart.repository.CartRepository;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.inventory.entity.Inventory;
import com.locnguyen.ecommerce.domains.inventory.entity.Warehouse;
import com.locnguyen.ecommerce.domains.inventory.repository.InventoryRepository;
import com.locnguyen.ecommerce.domains.inventory.service.InventoryService;
import com.locnguyen.ecommerce.domains.order.dto.CreateOrderRequest;
import com.locnguyen.ecommerce.domains.order.dto.OrderPreviewResponse;
import com.locnguyen.ecommerce.domains.order.dto.OrderAdminFilter;
import com.locnguyen.ecommerce.domains.order.dto.OrderResponse;
import com.locnguyen.ecommerce.domains.order.dto.OrderFilter;
import com.locnguyen.ecommerce.domains.order.dto.OrderItemResponse;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.entity.OrderItem;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.order.mapper.OrderMapper;
import com.locnguyen.ecommerce.domains.order.repository.OrderItemRepository;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.shipment.service.ShipmentService;
import com.locnguyen.ecommerce.domains.shipment.service.ShipmentListSummary;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import com.locnguyen.ecommerce.domains.order.service.impl.OrderServiceImpl;
import com.locnguyen.ecommerce.domains.invoice.service.InvoiceService;
import com.locnguyen.ecommerce.domains.idempotency.entity.IdempotencyKey;
import com.locnguyen.ecommerce.domains.idempotency.enums.IdempotencyStatus;
import com.locnguyen.ecommerce.domains.idempotency.service.IdempotencyService;
import com.locnguyen.ecommerce.domains.notification.service.NotificationService;
import com.locnguyen.ecommerce.domains.payment.service.PaymentService;
import com.locnguyen.ecommerce.domains.product.entity.Product;
import com.locnguyen.ecommerce.domains.product.entity.ProductMedia;
import com.locnguyen.ecommerce.domains.product.enums.MediaType;
import com.locnguyen.ecommerce.domains.product.repository.ProductMediaRepository;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import com.locnguyen.ecommerce.domains.promotion.dto.CheckoutVoucher;
import com.locnguyen.ecommerce.domains.promotion.dto.ValidateVoucherResponse;
import com.locnguyen.ecommerce.domains.promotion.service.VoucherService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
/**
 * Unit tests for {@link OrderService}.
 *
 * All dependencies are mocked. Tests cover:
 * - Order creation flow (cart, address, inventory, payment)
 * - State machine transitions (confirm, cancel, complete)
 * - Ownership enforcement for customer-facing endpoints
 * - Edge cases: empty cart, wrong address, insufficient stock
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    private static UUID uuid(long n) { return new UUID(0L, n); }

    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock OrderMapper orderMapper;
    @Mock CartRepository cartRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock AddressRepository addressRepository;
    @Mock InventoryService inventoryService;
    @Mock OrderHoldService orderHoldService;
    @Mock InventoryRepository inventoryRepository;
    @Mock PaymentService paymentService;
    @Mock AuditLogService auditLogService;
    @Mock NotificationService notificationService;
    @Mock IdempotencyService idempotencyService;
    @Mock CarrierCheckoutService carrierCheckoutService;
    @Mock ProductMediaRepository productMediaRepository;
    @Mock VoucherService voucherService;
    @Mock ShipmentService shipmentService;
    @Mock InvoiceService invoiceService;

    @InjectMocks OrderServiceImpl orderService;

    // ─── factories ───────────────────────────────────────────────────────────

    private Customer customer(UUID id) {
        Customer c = mock(Customer.class);
        when(c.getId()).thenReturn(id);
        User user = new User();
        user.setFirstName("Lan");
        user.setLastName("Nguyen");
        user.setEmail("lan@example.com");
        when(c.getUser()).thenReturn(user);
        return c;
    }

    @Test
    void customerOrderHistoryLoadsPreviewInOneBatchAndPreservesUnitCount() {
        Customer owner = customer(uuid(20));
        Order order = new Order();
        setId(order, uuid(21));
        Order secondOrder = new Order();
        setId(secondOrder, uuid(22));
        var pageable = PageRequest.of(0, 2);
        when(orderRepository.filter(owner.getId(), null, pageable))
                .thenReturn(new PageImpl<>(List.of(order, secondOrder), pageable, 7));
        List<OrderItem> items = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setQuantity(2);
            items.add(item);
        }
        when(orderItemRepository.findByOrderIdInOrderByCreatedAtAscIdAsc(List.of(order.getId(), secondOrder.getId())))
                .thenReturn(items);
        List<OrderItemResponse> preview = List.of(
                OrderItemResponse.builder().productName("Blazer").build(),
                OrderItemResponse.builder().productName("Trousers").build(),
                OrderItemResponse.builder().productName("Shirt").build());
        when(orderMapper.toItemResponses(items.subList(0, 3))).thenReturn(preview);

        var history = orderService.getMyOrders(owner, new OrderFilter(), pageable);

        assertThat(history.getTotalItems()).isEqualTo(7);
        assertThat(history.getItems().get(0).getItemsPreview()).isEqualTo(preview);
        assertThat(history.getItems().get(0).getRemainingItemLines()).isEqualTo(1);
        assertThat(history.getItems().get(0).getTotalItems()).isEqualTo(8);
        assertThat(history.getItems().get(1).getRemainingItemLines()).isZero();
        verify(orderItemRepository).findByOrderIdInOrderByCreatedAtAscIdAsc(List.of(order.getId(), secondOrder.getId()));
        verifyNoMoreInteractions(orderItemRepository);
    }

    private Cart activeCart(UUID cartId, Customer customer) {
        Cart cart = new Cart();
        setId(cart, cartId);
        cart.setCustomer(customer);
        cart.setStatus(CartStatus.ACTIVE);
        cart.setItems(new ArrayList<>());
        return cart;
    }

    private Address address(UUID id, Customer owner) {
        Address a = new Address();
        setId(a, id);
        a.setCustomer(owner);
        a.setReceiverName("Nguyen Van A");
        a.setPhoneNumber("0901234567");
        a.setStreetAddress("123 Street");
        a.setWard("Ward 1");
        a.setDistrict("District 1");
        a.setCity("Ho Chi Minh");
        a.setPostalCode("700000");
        return a;
    }

    private ProductVariant variant(UUID id, BigDecimal basePrice, BigDecimal salePrice) {
        Product product = new Product();
        setId(product, uuid(1000 + id.getLeastSignificantBits()));
        ReflectionTestUtils.setField(product, "name", "Test Product");

        ProductVariant v = new ProductVariant();
        setId(v, id);
        v.setProduct(product);
        v.setVariantName("White / M");
        v.setSku("SKU-001");
        v.setBasePrice(basePrice);
        v.setSalePrice(salePrice);
        return v;
    }

    private CartItem cartItem(Cart cart, ProductVariant variant, int quantity) {
        CartItem ci = new CartItem();
        setId(ci, UUID.randomUUID());
        ci.setCart(cart);
        ci.setVariant(variant);
        ci.setQuantity(quantity);
        return ci;
    }

    private ProductMedia media(UUID id, Product product, ProductVariant variant, String mediaUrl,
                               boolean primary, int sortOrder) {
        ProductMedia media = new ProductMedia();
        setId(media, id);
        media.setProduct(product);
        media.setVariant(variant);
        media.setMediaUrl(mediaUrl);
        media.setMediaType(MediaType.IMAGE);
        media.setPrimary(primary);
        media.setSortOrder(sortOrder);
        return media;
    }

    private Inventory inventory(UUID id, ProductVariant variant, int onHand, int reserved) {
        Warehouse warehouse = new Warehouse();
        setId(warehouse, uuid(1));

        Inventory inv = new Inventory();
        setId(inv, id);
        inv.setVariant(variant);
        inv.setWarehouse(warehouse);
        inv.setOnHand(onHand);
        inv.setReserved(reserved);
        return inv;
    }

    private Order savedOrder(UUID id, Customer customer, OrderStatus status,
                              PaymentMethod paymentMethod, BigDecimal total) {
        Order order = new Order();
        setId(order, id);
        order.setCustomer(customer);
        order.setOrderCode("ORD202604060001");
        order.setStatus(status);
        order.setPaymentMethod(paymentMethod);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setTotalAmount(total);
        order.setSubTotal(total);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setShippingFee(BigDecimal.ZERO);
        order.setItems(new ArrayList<>());
        return order;
    }

    private CreateOrderRequest createRequest(UUID addressId, String paymentMethod) {
        return createRequest(addressId, paymentMethod, null);
    }

    private CreateOrderRequest createRequest(UUID addressId, String paymentMethod, UUID carrierId) {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setShippingAddressId(addressId);
        req.setPaymentMethod(paymentMethod == null ? null : PaymentMethod.valueOf(paymentMethod));
        req.setCarrierId(carrierId);
        return req;
    }

    private static void setId(Object entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }

    // ─── setupCommonStubsForCreateOrder ─────────────────────────────────────

    @BeforeEach
    void stubCommon() {
        lenient().when(orderRepository.findByIdWithLock(any()))
                .thenAnswer(invocation -> orderRepository.findById(invocation.getArgument(0)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) setId(o, uuid(99));
            return o;
        });
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        // Default idempotency stub: always return a fresh PROCESSING record so all
        // CreateOrder tests reach the business logic uninterrupted.
        IdempotencyKey processingIdem = new IdempotencyKey();
        ReflectionTestUtils.setField(processingIdem, "id", 1L);
        processingIdem.setStatus(IdempotencyStatus.PROCESSING);
        when(idempotencyService.findOrCreateProcessing(any(), any(), any(), any()))
                .thenReturn(processingIdem);
    }

    @Test
    void adminOrderListKeepsHistoricalSnapshotWhenSoftDeletedCustomerProfileIsUnavailable() {
        Order historicalOrder = new Order();
        setId(historicalOrder, uuid(501));
        ReflectionTestUtils.setField(historicalOrder, "customerId", uuid(1));
        historicalOrder.setCustomer(null);
        historicalOrder.setCustomerNameSnapshot("Lan Nguyen");
        historicalOrder.setCustomerEmailSnapshot("lan@example.com");
        historicalOrder.setOrderCode("ORD-HISTORIC");
        historicalOrder.setItems(new ArrayList<>());
        historicalOrder.setTotalAmount(BigDecimal.TEN);

        User currentUser = new User();
        currentUser.setFirstName("Updated");
        currentUser.setLastName("Profile");
        currentUser.setEmail("updated@example.com");
        Customer activeCustomer = mock(Customer.class);
        when(activeCustomer.getUser()).thenReturn(currentUser);
        Order legacyOrder = new Order();
        setId(legacyOrder, uuid(502));
        ReflectionTestUtils.setField(legacyOrder, "customerId", uuid(2));
        legacyOrder.setCustomer(activeCustomer);
        legacyOrder.setCustomerNameSnapshot(null);
        legacyOrder.setCustomerEmailSnapshot(null);
        legacyOrder.setOrderCode("ORD-LEGACY");
        legacyOrder.setItems(new ArrayList<>());
        legacyOrder.setTotalAmount(BigDecimal.ONE);

        when(orderRepository.adminSearch(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(historicalOrder, legacyOrder), PageRequest.of(0, 20), 2));
        when(shipmentService.getListSummaries(anyList())).thenReturn(List.of());
        when(orderItemRepository.countQuantitiesByOrderIds(anyList())).thenReturn(List.of());

        var result = orderService.getAllOrders(new OrderAdminFilter(), PageRequest.of(0, 20));

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0)).satisfies(item -> {
            assertThat(item.getCustomerId()).isEqualTo(uuid(1));
            assertThat(item.getCustomerNameSnapshot()).isEqualTo("Lan Nguyen");
            assertThat(item.getCustomerEmailSnapshot()).isEqualTo("lan@example.com");
            assertThat(item.isCustomerSnapshotAvailable()).isTrue();
            assertThat(item.isCurrentCustomerProfileAvailable()).isFalse();
            assertThat(item.getCustomerName()).isNull();
            assertThat(item.getCustomerEmail()).isNull();
        });
        assertThat(result.getItems().get(1)).satisfies(item -> {
            assertThat(item.getCustomerNameSnapshot()).isNull();
            assertThat(item.getCustomerEmailSnapshot()).isNull();
            assertThat(item.isCustomerSnapshotAvailable()).isFalse();
            assertThat(item.getCustomerName()).isEqualTo("Updated Profile");
            assertThat(item.getCustomerEmail()).isEqualTo("updated@example.com");
            assertThat(item.isCurrentCustomerProfileAvailable()).isTrue();
        });
    }

    @Test
    void adminOrderDetailSeparatesSnapshotFromCurrentProfileSummary() {
        Order order = new Order();
        setId(order, uuid(503));
        ReflectionTestUtils.setField(order, "customerId", uuid(3));
        order.setCustomerNameSnapshot("Checkout Name");
        order.setCustomerEmailSnapshot("checkout@example.com");
        order.setItems(new ArrayList<>());
        User user = new User();
        user.setFirstName("Current");
        user.setLastName("Name");
        user.setEmail("current@example.com");
        Customer customer = mock(Customer.class);
        when(customer.getUser()).thenReturn(user);
        order.setCustomer(customer);
        when(orderRepository.findAdminByIdWithCurrentCustomer(uuid(503))).thenReturn(Optional.of(order));
        OrderResponse response = orderService.getOrderByIdAdmin(uuid(503));

        assertThat(response.getCustomerNameSnapshot()).isEqualTo("Checkout Name");
        assertThat(response.getCustomerEmailSnapshot()).isEqualTo("checkout@example.com");
        assertThat(response.getCurrentCustomerName()).isEqualTo("Current Name");
        assertThat(response.getCurrentCustomerEmail()).isEqualTo("current@example.com");
        assertThat(response.getCurrentCustomerProfileAvailable()).isTrue();
    }

    @Test
    void adminOrderListAddsShipmentAttentionWithoutChangingOrderState() {
        Order failed = new Order();
        setId(failed, uuid(510));
        failed.setOrderCode("ORD-FAILED");
        failed.setStatus(OrderStatus.SHIPPED);
        failed.setPaymentStatus(PaymentStatus.PENDING);
        failed.setTotalAmount(BigDecimal.TEN);
        Order returned = new Order();
        setId(returned, uuid(511));
        returned.setOrderCode("ORD-RETURNED");
        returned.setStatus(OrderStatus.SHIPPED);
        returned.setPaymentStatus(PaymentStatus.PENDING);
        returned.setTotalAmount(BigDecimal.ONE);
        Order latePaid = new Order();
        setId(latePaid, uuid(512));
        latePaid.setOrderCode("ORD-LATE-PAID");
        latePaid.setStatus(OrderStatus.CANCELLED);
        latePaid.setPaymentStatus(PaymentStatus.PAID);
        latePaid.setTotalAmount(BigDecimal.ONE);

        when(orderRepository.adminSearch(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(failed, returned, latePaid), PageRequest.of(0, 20), 3));
        when(orderItemRepository.countQuantitiesByOrderIds(anyList())).thenReturn(List.of());
        when(shipmentService.getListSummaries(anyList())).thenReturn(List.of(
                new ShipmentListSummary(uuid(510), ShipmentStatus.FAILED, "Carrier", "TRACK-1"),
                new ShipmentListSummary(uuid(511), ShipmentStatus.RETURNED, "Carrier", "TRACK-2")));

        var result = orderService.getAllOrders(new OrderAdminFilter(), PageRequest.of(0, 20));

        assertThat(result.getItems()).extracting("attention")
                .containsExactly("DELIVERY_FAILED", "RETURNED_HANDLING", "REFUND_REQUIRED");
        assertThat(result.getItems().get(0).getTrackingNumber()).isEqualTo("TRACK-1");
        assertThat(failed.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(returned.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(latePaid.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderItemRepository, times(1)).countQuantitiesByOrderIds(anyList());
        verify(shipmentService, times(1)).getListSummaries(anyList());
    }

    @Test
    void adminOrderListRejectsUnsupportedSortField() {
        assertThatThrownBy(() -> orderService.getAllOrders(new OrderAdminFilter(),
                PageRequest.of(0, 20, org.springframework.data.domain.Sort.by("customerEmail"))))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void adminOrderListCombinesSearchFiltersAndServerSortInOnePageQuery() {
        OrderAdminFilter filter = new OrderAdminFilter();
        filter.setKeyword("  ORD-55  ");
        filter.setStatus(OrderStatus.PROCESSING);
        filter.setPaymentStatus(PaymentStatus.FAILED);
        filter.setPaymentMethod(PaymentMethod.ONLINE);
        filter.setShipmentStatus(ShipmentStatus.IN_TRANSIT);
        LocalDateTime from = LocalDateTime.parse("2026-01-01T00:00:00");
        LocalDateTime to = LocalDateTime.parse("2026-01-31T23:59:59");
        filter.setCreatedFrom(from);
        filter.setCreatedTo(to);
        when(orderRepository.adminSearch(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        when(shipmentService.getListSummaries(anyList())).thenReturn(List.of());

        orderService.getAllOrders(filter,
                PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("createdAt").ascending()));

        verify(orderRepository).adminSearch(isNull(), eq(OrderStatus.PROCESSING), eq(PaymentStatus.FAILED),
                eq(PaymentMethod.ONLINE), eq(from), eq(to), eq("ORD-55"), eq(ShipmentStatus.IN_TRANSIT),
                argThat(pageable -> pageable.getSort().getOrderFor("createdAt").isAscending()));
        verify(shipmentService, times(1)).getListSummaries(anyList());
    }

    // ─── createOrder ─────────────────────────────────────────────────────────

    @Nested
    class CreateOrder {

        @Test
        void throws_CART_NOT_FOUND_when_no_active_cart() {
            Customer cust = customer(uuid(1));
            when(cartRepository.findByCustomerIdAndStatusWithLock(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createOrder(cust, createRequest(uuid(10), null), "idem-key"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CART_NOT_FOUND);
        }

        @Test
        void throws_ORDER_EMPTY_when_cart_has_no_items() {
            Customer cust = customer(uuid(1));
            Cart cart = activeCart(uuid(5), cust);
            when(cartRepository.findByCustomerIdAndStatusWithLock(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(uuid(5))).thenReturn(List.of());

            assertThatThrownBy(() -> orderService.createOrder(cust, createRequest(uuid(10), null), "idem-key"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_EMPTY);
        }

        @Test
        void throws_ADDRESS_NOT_FOUND_when_address_missing() {
            Customer cust = customer(uuid(1));
            Cart cart = activeCart(uuid(5), cust);
            ProductVariant v = variant(uuid(1), new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 2);

            when(cartRepository.findByCustomerIdAndStatusWithLock(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(uuid(5))).thenReturn(List.of(ci));
            when(addressRepository.findByIdAndDeletedFalse(uuid(99))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createOrder(cust, createRequest(uuid(99), null), "idem-key"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ADDRESS_NOT_FOUND);
        }

        @Test
        void throws_ADDRESS_NOT_FOUND_when_address_belongs_to_another_customer() {
            Customer cust = customer(uuid(1));
            Customer otherCustomer = customer(uuid(999));
            Cart cart = activeCart(uuid(5), cust);
            ProductVariant v = variant(uuid(1), new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 2);
            Address addr = address(uuid(10), otherCustomer);  // different owner

            when(cartRepository.findByCustomerIdAndStatusWithLock(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(uuid(5))).thenReturn(List.of(ci));
            when(addressRepository.findByIdAndDeletedFalse(uuid(10))).thenReturn(Optional.of(addr));

            assertThatThrownBy(() -> orderService.createOrder(cust, createRequest(uuid(10), null), "idem-key"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ADDRESS_NOT_FOUND);
        }

        @Test
        void throws_INVENTORY_NOT_FOUND_when_no_inventory_for_variant() {
            Customer cust = customer(uuid(1));
            Cart cart = activeCart(uuid(5), cust);
            ProductVariant v = variant(uuid(1), new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 2);
            Address addr = address(uuid(10), cust);

            when(cartRepository.findByCustomerIdAndStatusWithLock(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(uuid(5))).thenReturn(List.of(ci));
            when(addressRepository.findByIdAndDeletedFalse(uuid(10))).thenReturn(Optional.of(addr));
            when(inventoryRepository.findByVariantIdIn(List.of(uuid(1)))).thenReturn(List.of());

            assertThatThrownBy(() -> orderService.createOrder(cust, createRequest(uuid(10), null), "idem-key"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVENTORY_NOT_FOUND);
        }

        @Test
        void cod_order_starts_in_PENDING_status() {
            Customer cust = customer(uuid(1));
            Cart cart = activeCart(uuid(5), cust);
            ProductVariant v = variant(uuid(1), new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 1);
            Address addr = address(uuid(10), cust);
            Inventory inv = inventory(uuid(20), v, 10, 0);

            stubSuccessfulCreateOrder(cust, cart, List.of(ci), addr, List.of(inv));

            orderService.createOrder(cust, createRequest(uuid(10), "COD"), "idem-key");

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(captor.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.COD);
            assertThat(captor.getValue().getCustomerNameSnapshot()).isEqualTo("Lan Nguyen");
            assertThat(captor.getValue().getCustomerEmailSnapshot()).isEqualTo("lan@example.com");
        }

        @Test
        void createOrder_persists_validated_voucher_discount_in_order_snapshot() {
            Customer cust = customer(uuid(1));
            Cart cart = activeCart(uuid(5), cust);
            ProductVariant variant = variant(uuid(1), new BigDecimal("150000"), null);
            CartItem item = cartItem(cart, variant, 2);
            Address addr = address(uuid(10), cust);
            Inventory inventory = inventory(uuid(20), variant, 10, 0);
            CreateOrderRequest request = createRequest(uuid(10), "COD");
            request.setVoucherCode("SAVE50");

            stubSuccessfulCreateOrder(cust, cart, List.of(item), addr, List.of(inventory));
            when(voucherService.redeemCheckoutVoucher(eq("SAVE50"), eq(cust), any(), anyList()))
                    .thenReturn(new CheckoutVoucher(ValidateVoucherResponse.builder()
                            .voucherCode("SAVE50")
                            .discountAmount(new BigDecimal("50000"))
                            .build(), java.util.Set.of(variant.getId())));

            orderService.createOrder(cust, request, "idem-key");

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getVoucherCode()).isEqualTo("SAVE50");
            assertThat(captor.getValue().getDiscountAmount()).isEqualByComparingTo("50000");
            assertThat(captor.getValue().getItems().get(0).getVoucherDiscountAmount()).isEqualByComparingTo("50000");
            assertThat(captor.getValue().getItems().get(0).getNetLineAmount()).isEqualByComparingTo("250000");
            assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("250000");
        }

        @Test
        void online_order_starts_in_AWAITING_PAYMENT_status() {
            Customer cust = customer(uuid(1));
            Cart cart = activeCart(uuid(5), cust);
            ProductVariant v = variant(uuid(1), new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 1);
            Address addr = address(uuid(10), cust);
            Inventory inv = inventory(uuid(20), v, 10, 0);

            stubSuccessfulCreateOrder(cust, cart, List.of(ci), addr, List.of(inv));

            orderService.createOrder(cust, createRequest(uuid(10), "ONLINE"), "idem-key");

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
            assertThat(captor.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.ONLINE);
        }

        @Test
        void order_item_snapshots_primary_variant_image_when_available() {
            Customer cust = customer(uuid(1));
            Cart cart = activeCart(uuid(5), cust);
            ProductVariant v = variant(uuid(1), new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 1);
            Address addr = address(uuid(10), cust);
            Inventory inv = inventory(uuid(20), v, 10, 0);
            ProductMedia variantImage = media(uuid(30), v.getProduct(), v,
                    "https://cdn.example.com/variant-primary.jpg", true, 10);
            ProductMedia earlierVariantImage = media(uuid(31), v.getProduct(), v,
                    "https://cdn.example.com/variant-earlier.jpg", false, 0);

            stubSuccessfulCreateOrder(cust, cart, List.of(ci), addr, List.of(inv));
            when(productMediaRepository.findByProductIdAndVariantIdAndMediaTypeOrderBySortOrderAsc(
                    v.getProduct().getId(), v.getId(), MediaType.IMAGE))
                    .thenReturn(List.of(earlierVariantImage, variantImage));

            orderService.createOrder(cust, createRequest(uuid(10), "COD"), "idem-key");

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getItems())
                    .singleElement()
                    .extracting(OrderItem::getProductImageUrl)
                    .isEqualTo("https://cdn.example.com/variant-primary.jpg");
        }

        @Test
        void order_item_falls_back_to_product_image_when_variant_has_no_image() {
            Customer cust = customer(uuid(1));
            Cart cart = activeCart(uuid(5), cust);
            ProductVariant v = variant(uuid(1), new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 1);
            Address addr = address(uuid(10), cust);
            Inventory inv = inventory(uuid(20), v, 10, 0);
            ProductMedia productImage = media(uuid(30), v.getProduct(), null,
                    "https://cdn.example.com/product-primary.jpg", true, 0);

            stubSuccessfulCreateOrder(cust, cart, List.of(ci), addr, List.of(inv));
            when(productMediaRepository.findByProductIdAndVariantIdAndMediaTypeOrderBySortOrderAsc(
                    v.getProduct().getId(), v.getId(), MediaType.IMAGE))
                    .thenReturn(List.of());
            when(productMediaRepository.findByProductIdAndVariantIsNullAndMediaTypeOrderBySortOrderAsc(
                    v.getProduct().getId(), MediaType.IMAGE))
                    .thenReturn(List.of(productImage));

            orderService.createOrder(cust, createRequest(uuid(10), "COD"), "idem-key");

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getItems())
                    .singleElement()
                    .extracting(OrderItem::getProductImageUrl)
                    .isEqualTo("https://cdn.example.com/product-primary.jpg");
        }

        @Test
        void cod_order_creates_cod_payment_record() {
            Customer cust = customer(uuid(1));
            Cart cart = activeCart(uuid(5), cust);
            ProductVariant v = variant(uuid(1), new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 1);
            Address addr = address(uuid(10), cust);
            Inventory inv = inventory(uuid(20), v, 10, 0);

            stubSuccessfulCreateOrder(cust, cart, List.of(ci), addr, List.of(inv));

            orderService.createOrder(cust, createRequest(uuid(10), "COD"), "idem-key");

            verify(paymentService).createCodPayment(any(Order.class));
        }

        @Test
        void online_order_does_NOT_create_cod_payment() {
            Customer cust = customer(uuid(1));
            Cart cart = activeCart(uuid(5), cust);
            ProductVariant v = variant(uuid(1), new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 1);
            Address addr = address(uuid(10), cust);
            Inventory inv = inventory(uuid(20), v, 10, 0);

            stubSuccessfulCreateOrder(cust, cart, List.of(ci), addr, List.of(inv));

            orderService.createOrder(cust, createRequest(uuid(10), "ONLINE"), "idem-key");

            verify(paymentService, never()).createCodPayment(any());
        }

        @Test
        void selected_carrier_applies_shipping_fee_and_snapshot_fields() {
            Customer cust = customer(uuid(1));
            Cart cart = activeCart(uuid(5), cust);
            ProductVariant v = variant(uuid(1), new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 1);
            Address addr = address(uuid(10), cust);
            Inventory inv = inventory(uuid(20), v, 10, 0);
            UUID carrierId = uuid(77);

            stubSuccessfulCreateOrder(cust, cart, List.of(ci), addr, List.of(inv));
            when(carrierCheckoutService.quote(eq(carrierId), any(Order.class)))
                    .thenReturn(CheckoutCarrierQuoteResponse.builder()
                            .carrierId(carrierId)
                            .carrierCode("AHAMOVE")
                            .carrierName("AhaMove")
                            .carrierProviderType(CarrierProviderType.AHAMOVE)
                            .shippingFee(new BigDecimal("25000"))
                            .currency("VND")
                            .serviceName("BIKE")
                            .deliveryEstimate(DeliveryEstimateResponse.configuredSla("BIKE", 1, 2))
                            .build());

            OrderResponse response = orderService.createOrder(cust, createRequest(uuid(10), "COD", carrierId), "idem-key");

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getCarrierId()).isEqualTo(carrierId);
            assertThat(captor.getValue().getCarrierCode()).isEqualTo("AHAMOVE");
            assertThat(captor.getValue().getCarrierName()).isEqualTo("AhaMove");
            assertThat(captor.getValue().getCarrierProviderType()).isEqualTo(CarrierProviderType.AHAMOVE);
            assertThat(captor.getValue().getShippingFee()).isEqualByComparingTo("25000");
            assertThat(captor.getValue().getDeliverySla().getMaxBusinessDays()).isEqualTo(2);
            assertThat(response.getDeliveryEstimate()).isEqualTo(DeliveryEstimateResponse.configuredSla("BIKE", 1, 2));
            assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("125000");
        }

        @Test
        void cart_is_marked_CHECKED_OUT_after_order_creation() {
            Customer cust = customer(uuid(1));
            Cart cart = activeCart(uuid(5), cust);
            ProductVariant v = variant(uuid(1), new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 1);
            Address addr = address(uuid(10), cust);
            Inventory inv = inventory(uuid(20), v, 10, 0);

            stubSuccessfulCreateOrder(cust, cart, List.of(ci), addr, List.of(inv));

            orderService.createOrder(cust, createRequest(uuid(10), "COD"), "idem-key");

            verify(cartRepository).save(argThat(c -> c.getStatus() == CartStatus.CHECKED_OUT));
        }

        @Test
        void subTotal_uses_salePrice_when_set() {
            // Variant has basePrice=200,000 and salePrice=150,000
            // Expected subTotal = 150,000 * 2 = 300,000
            Customer cust = customer(uuid(1));
            Cart cart = activeCart(uuid(5), cust);
            ProductVariant v = variant(uuid(1), new BigDecimal("200000"), new BigDecimal("150000"));
            CartItem ci = cartItem(cart, v, 2);
            Address addr = address(uuid(10), cust);
            Inventory inv = inventory(uuid(20), v, 10, 0);

            stubSuccessfulCreateOrder(cust, cart, List.of(ci), addr, List.of(inv));

            orderService.createOrder(cust, createRequest(uuid(10), "COD"), "idem-key");

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getSubTotal()).isEqualByComparingTo("300000");
            assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("300000");
        }

        @Test
        void subTotal_uses_basePrice_when_salePrice_is_null() {
            // Variant has basePrice=100,000 and no salePrice
            Customer cust = customer(uuid(1));
            Cart cart = activeCart(uuid(5), cust);
            ProductVariant v = variant(uuid(1), new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 3);
            Address addr = address(uuid(10), cust);
            Inventory inv = inventory(uuid(20), v, 10, 0);

            stubSuccessfulCreateOrder(cust, cart, List.of(ci), addr, List.of(inv));

            orderService.createOrder(cust, createRequest(uuid(10), "COD"), "idem-key");

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getSubTotal()).isEqualByComparingTo("300000");
        }

        @Test
        void shipping_address_fields_are_snapshotted_from_address_entity() {
            Customer cust = customer(uuid(1));
            Cart cart = activeCart(uuid(5), cust);
            ProductVariant v = variant(uuid(1), new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 1);
            Address addr = address(uuid(10), cust);  // fields set in factory
            Inventory inv = inventory(uuid(20), v, 5, 0);

            stubSuccessfulCreateOrder(cust, cart, List.of(ci), addr, List.of(inv));

            orderService.createOrder(cust, createRequest(uuid(10), "COD"), "idem-key");

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            Order saved = captor.getValue();
            assertThat(saved.getReceiverName()).isEqualTo("Nguyen Van A");
            assertThat(saved.getReceiverPhone()).isEqualTo("0901234567");
            assertThat(saved.getShippingCity()).isEqualTo("Ho Chi Minh");
        }

        @Test
        void picks_warehouse_with_most_available_stock() {
            Customer cust = customer(uuid(1));
            Cart cart = activeCart(uuid(5), cust);
            ProductVariant v = variant(uuid(1), new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 1);
            Address addr = address(uuid(10), cust);

            // Warehouse A: onHand=5, reserved=3 → available=2
            Inventory invA = inventory(uuid(20), v, 5, 3);
            setId(invA.getWarehouse(), uuid(1));

            // Warehouse B: onHand=20, reserved=2 → available=18 ← best
            Inventory invB = inventory(uuid(21), v, 20, 2);
            setId(invB.getWarehouse(), uuid(2));

            stubSuccessfulCreateOrder(cust, cart, List.of(ci), addr, List.of(invA, invB));

            orderService.createOrder(cust, createRequest(uuid(10), "COD"), "idem-key");

            // Should reserve from warehouse 2 (available=18 > available=2)
            verify(inventoryService).reserveStock(argThat(req -> uuid(2).equals(req.getWarehouseId())));
        }

        @Test
        void reserve_is_called_once_per_cart_item() {
            Customer cust = customer(uuid(1));
            Cart cart = activeCart(uuid(5), cust);
            ProductVariant v1 = variant(uuid(1), new BigDecimal("100000"), null);
            ProductVariant v2 = variant(uuid(2), new BigDecimal("200000"), null);
            CartItem ci1 = cartItem(cart, v1, 1);
            CartItem ci2 = cartItem(cart, v2, 2);
            Address addr = address(uuid(10), cust);
            Inventory inv1 = inventory(uuid(20), v1, 10, 0);
            Inventory inv2 = inventory(uuid(21), v2, 10, 0);

            when(cartRepository.findByCustomerIdAndStatusWithLock(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(uuid(5)))
                    .thenReturn(List.of(ci1, ci2));
            when(addressRepository.findByIdAndDeletedFalse(uuid(10))).thenReturn(Optional.of(addr));
            when(inventoryRepository.findByVariantIdIn(List.of(uuid(1), uuid(2))))
                    .thenReturn(List.of(inv1, inv2));
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(any(UUID.class)))
                    .thenReturn(List.of());

            orderService.createOrder(cust, createRequest(uuid(10), "COD"), "idem-key");

            verify(inventoryService, times(2)).reserveStock(any());
        }

        // helper: stubs all the happy-path repository calls for a single-item order
        private void stubSuccessfulCreateOrder(Customer cust, Cart cart, List<CartItem> items,
                                                Address addr, List<Inventory> inventories) {
            when(cartRepository.findByCustomerIdAndStatusWithLock(cust.getId(), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cart));
            when(cartRepository.findByCustomerIdAndStatus(cust.getId(), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId()))
                    .thenReturn(items);
            when(addressRepository.findByIdAndDeletedFalse(addr.getId())).thenReturn(Optional.of(addr));
            List<UUID> variantIds = items.stream()
                    .map(ci -> ci.getVariant().getId())
                    .toList();
            when(inventoryRepository.findByVariantIdIn(variantIds)).thenReturn(inventories);
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(any(UUID.class)))
                    .thenReturn(List.of());
        }
    }

    @Nested
    class PreviewOrder {

        @Test
        void previewOrder_returns_server_calculated_shipping_and_total() {
            Customer cust = customer(uuid(1));
            Cart cart = activeCart(uuid(5), cust);
            ProductVariant v = variant(uuid(1), new BigDecimal("150000"), null);
            CartItem ci = cartItem(cart, v, 2);
            Address addr = address(uuid(10), cust);
            UUID carrierId = uuid(88);

            when(cartRepository.findByCustomerIdAndStatus(cust.getId(), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId()))
                    .thenReturn(List.of(ci));
            when(addressRepository.findByIdAndDeletedFalse(addr.getId())).thenReturn(Optional.of(addr));
            when(carrierCheckoutService.quote(eq(carrierId), any(Order.class)))
                    .thenReturn(CheckoutCarrierQuoteResponse.builder()
                            .carrierId(carrierId)
                            .carrierCode("AHAMOVE")
                            .carrierName("Ahamove")
                            .carrierProviderType(CarrierProviderType.AHAMOVE)
                            .shippingFee(new BigDecimal("30000"))
                            .currency("VND")
                            .serviceName("Bike")
                            .deliveryEstimate(DeliveryEstimateResponse.configuredSla("SGN-BIKE", 1, 2))
                            .build());

            OrderPreviewResponse response = orderService.previewOrder(
                    cust, createRequest(uuid(10), "ONLINE", carrierId));

            assertThat(response.getCarrierId()).isEqualTo(carrierId);
            assertThat(response.getCarrierCode()).isEqualTo("AHAMOVE");
            assertThat(response.getShippingFee()).isEqualByComparingTo("30000");
            assertThat(response.getTotalAmount()).isEqualByComparingTo("330000");
            assertThat(response.getShippingServiceName()).isEqualTo("Bike");
            assertThat(response.getDeliveryEstimate()).isEqualTo(DeliveryEstimateResponse.configuredSla("SGN-BIKE", 1, 2));
        }

        @Test
        void previewOrder_applies_the_validated_voucher_discount_to_total() {
            Customer cust = customer(uuid(1));
            Cart cart = activeCart(uuid(5), cust);
            ProductVariant variant = variant(uuid(1), new BigDecimal("150000"), null);
            CartItem item = cartItem(cart, variant, 2);
            Address addr = address(uuid(10), cust);
            CreateOrderRequest request = createRequest(addr.getId(), "COD");
            request.setVoucherCode("SAVE50");

            when(cartRepository.findByCustomerIdAndStatus(cust.getId(), CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId())).thenReturn(List.of(item));
            when(addressRepository.findByIdAndDeletedFalse(addr.getId())).thenReturn(Optional.of(addr));
            when(voucherService.quoteCheckoutVoucher(eq("SAVE50"), eq(cust), anyList()))
                    .thenReturn(new CheckoutVoucher(ValidateVoucherResponse.builder()
                            .voucherCode("SAVE50")
                            .discountAmount(new BigDecimal("50000"))
                            .build(), java.util.Set.of(variant.getId())));

            OrderPreviewResponse response = orderService.previewOrder(cust, request);

            assertThat(response.getDiscountAmount()).isEqualByComparingTo("50000");
            assertThat(response.getTotalAmount()).isEqualByComparingTo("250000");
            assertThat(response.getVoucherCode()).isEqualTo("SAVE50");
        }
    }

    // ─── confirmOrder ─────────────────────────────────────────────────────────

    @Nested
    class ConfirmOrder {

        @Test
        void throws_ORDER_NOT_FOUND_when_order_does_not_exist() {
            when(orderRepository.findById(uuid(99))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.confirmOrder(uuid(99)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        void throws_ORDER_STATUS_INVALID_when_order_is_already_PROCESSING() {
            Customer cust = customer(uuid(1));
            Order order = savedOrder(uuid(1), cust, OrderStatus.PROCESSING, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.confirmOrder(uuid(1)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_STATUS_INVALID);
        }

        @Test
        void throws_ORDER_STATUS_INVALID_when_online_payment_not_paid() {
            Customer cust = customer(uuid(1));
            Order order = savedOrder(uuid(1), cust, OrderStatus.AWAITING_PAYMENT,
                    PaymentMethod.ONLINE, new BigDecimal("100000"));
            order.setPaymentStatus(PaymentStatus.PENDING);  // not PAID yet
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.confirmOrder(uuid(1)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_STATUS_INVALID);
        }

        @Test
        void throws_ORDER_STATUS_INVALID_when_online_order_is_PENDING_even_if_paid() {
            Customer cust = customer(uuid(1));
            Order order = savedOrder(uuid(1), cust, OrderStatus.PENDING,
                    PaymentMethod.ONLINE, new BigDecimal("100000"));
            order.setPaymentStatus(PaymentStatus.PAID);
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.confirmOrder(uuid(1)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_STATUS_INVALID);
            verify(orderRepository, never()).save(any());
        }

        @Test
        void cod_PENDING_order_can_be_confirmed_without_marking_payment_paid() {
            Customer cust = customer(uuid(1));
            Order order = savedOrder(uuid(1), cust, OrderStatus.PENDING, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(any(UUID.class))).thenReturn(List.of());

            orderService.confirmOrder(uuid(1));

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(captor.getValue().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        void cod_AWAITING_PAYMENT_order_can_be_confirmed_without_payment_status_check() {
            Customer cust = customer(uuid(1));
            Order order = savedOrder(uuid(1), cust, OrderStatus.AWAITING_PAYMENT, PaymentMethod.COD,
                    new BigDecimal("100000"));
            order.setPaymentStatus(PaymentStatus.PENDING);  // COD — not paid yet, but still confirming
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(any(UUID.class))).thenReturn(List.of());

            orderService.confirmOrder(uuid(1));

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        void online_order_can_be_confirmed_when_payment_is_PAID() {
            Customer cust = customer(uuid(1));
            Order order = savedOrder(uuid(1), cust, OrderStatus.AWAITING_PAYMENT,
                    PaymentMethod.ONLINE, new BigDecimal("100000"));
            order.setPaymentStatus(PaymentStatus.PAID);
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(any(UUID.class))).thenReturn(List.of());

            orderService.confirmOrder(uuid(1));

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        void repeated_confirmation_rejects_stale_status_without_duplicate_save() {
            Customer cust = customer(uuid(1));
            Order order = savedOrder(uuid(1), cust, OrderStatus.PENDING, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(any(UUID.class))).thenReturn(List.of());

            orderService.confirmOrder(uuid(1));

            assertThatThrownBy(() -> orderService.confirmOrder(uuid(1)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_STATUS_INVALID);
            verify(orderRepository, times(1)).save(any());
        }
    }

    @Nested
    class ProcessOrder {

        @Test
        void rejects_online_order_without_paid_status() {
            Customer cust = customer(uuid(1));
            Order order = savedOrder(uuid(1), cust, OrderStatus.CONFIRMED,
                    PaymentMethod.ONLINE, new BigDecimal("100000"));
            order.setPaymentStatus(PaymentStatus.PENDING);
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.processOrder(uuid(1)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_STATUS_INVALID);

            verify(orderRepository, never()).save(argThat(saved ->
                    saved.getStatus() == OrderStatus.PROCESSING));
        }

        @Test
        void allows_cod_order_while_payment_is_pending() {
            Customer cust = customer(uuid(1));
            Order order = savedOrder(uuid(1), cust, OrderStatus.CONFIRMED,
                    PaymentMethod.COD, new BigDecimal("100000"));
            order.setPaymentStatus(PaymentStatus.PENDING);
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));

            orderService.processOrder(uuid(1));

            verify(orderRepository).save(argThat(saved ->
                    saved.getStatus() == OrderStatus.PROCESSING));
        }
    }

    // ─── cancelOrder (admin) ─────────────────────────────────────────────────

    @Nested
    class CancelOrder {

        @Test
        void admin_can_cancel_CONFIRMED_order() {
            Customer cust = customer(uuid(1));
            Order order = savedOrder(uuid(1), cust, OrderStatus.CONFIRMED, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(any(UUID.class))).thenReturn(List.of());

            orderService.cancelOrder(uuid(1));

            verify(orderRepository).save(argThat(o -> o.getStatus() == OrderStatus.CANCELLED));
            verify(inventoryService).releaseStock("ORDER", order.getOrderCode());
            verify(invoiceService).voidByOrderId(uuid(1),
                    "Invoice voided because order " + order.getOrderCode() + " was cancelled");
        }

        @Test
        void throws_ORDER_CANNOT_CANCEL_when_order_is_SHIPPED() {
            Customer cust = customer(uuid(1));
            Order order = savedOrder(uuid(1), cust, OrderStatus.SHIPPED, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(uuid(1)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_CANNOT_CANCEL);
        }

        @Test
        void throws_ORDER_CANNOT_CANCEL_when_order_is_already_CANCELLED() {
            Customer cust = customer(uuid(1));
            Order order = savedOrder(uuid(1), cust, OrderStatus.CANCELLED, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(uuid(1)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_CANNOT_CANCEL);
        }

        @Test
        void releasing_stock_is_called_on_successful_cancel() {
            Customer cust = customer(uuid(1));
            Order order = savedOrder(uuid(1), cust, OrderStatus.PENDING, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(any(UUID.class))).thenReturn(List.of());

            orderService.cancelOrder(uuid(1));

            verify(inventoryService).releaseStock("ORDER", "ORD202604060001");
        }
    }

    // ─── cancelMyOrder (customer) ────────────────────────────────────────────

    @Nested
    class CancelMyOrder {

        @Test
        void retry_does_not_release_stock_or_void_invoice_twice() {
            Customer cust = customer(uuid(1));
            Order order = savedOrder(uuid(1), cust, OrderStatus.PENDING, PaymentMethod.COD,
                    new BigDecimal("100000"));
            order.setReceiverName("Original recipient");
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(any(UUID.class))).thenReturn(List.of());

            orderService.cancelMyOrder(uuid(1), cust);

            assertThatThrownBy(() -> orderService.cancelMyOrder(uuid(1), cust))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_CANNOT_CANCEL);
            assertThat(order.getReceiverName()).isEqualTo("Original recipient");
            verify(inventoryService).releaseStock("ORDER", order.getOrderCode());
            verify(invoiceService).voidByOrderId(eq(order.getId()), anyString());
        }


        @Test
        void customer_can_cancel_PENDING_order() {
            Customer cust = customer(uuid(1));
            Order order = savedOrder(uuid(1), cust, OrderStatus.PENDING, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(any(UUID.class))).thenReturn(List.of());

            orderService.cancelMyOrder(uuid(1), cust);

            verify(orderRepository).save(argThat(o -> o.getStatus() == OrderStatus.CANCELLED));
        }

        @Test
        void customer_can_cancel_AWAITING_PAYMENT_order() {
            Customer cust = customer(uuid(1));
            Order order = savedOrder(uuid(1), cust, OrderStatus.AWAITING_PAYMENT, PaymentMethod.ONLINE,
                    new BigDecimal("100000"));
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(any(UUID.class))).thenReturn(List.of());

            orderService.cancelMyOrder(uuid(1), cust);

            verify(orderRepository).save(argThat(o -> o.getStatus() == OrderStatus.CANCELLED));
        }

        @Test
        void customer_cannot_cancel_CONFIRMED_order() {
            Customer cust = customer(uuid(1));
            Order order = savedOrder(uuid(1), cust, OrderStatus.CONFIRMED, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelMyOrder(uuid(1), cust))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_CANNOT_CANCEL);
        }

        @Test
        void cannot_cancel_paid_online_order_without_refund() {
            Customer cust = customer(uuid(1));
            Order order = savedOrder(uuid(1), cust, OrderStatus.AWAITING_PAYMENT,
                    PaymentMethod.ONLINE, new BigDecimal("100000"));
            order.setPaymentStatus(PaymentStatus.PAID);
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(uuid(1)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_CANNOT_CANCEL);

            verify(inventoryService, never()).releaseStock(anyString(), anyString());
        }

        @Test
        void throws_ORDER_NOT_FOUND_when_order_belongs_to_another_customer() {
            Customer cust = customer(uuid(1));
            Customer otherCust = customer(uuid(999));
            Order order = savedOrder(uuid(1), otherCust, OrderStatus.PENDING, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelMyOrder(uuid(1), cust))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        }
    }

    // ─── completeOrder ────────────────────────────────────────────────────────

    @Nested
    class CompleteOrder {

        @Test
        void throws_ORDER_CANNOT_COMPLETE_when_order_is_not_DELIVERED() {
            Customer cust = customer(uuid(1));
            Order order = savedOrder(uuid(1), cust, OrderStatus.CONFIRMED, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.completeOrder(uuid(1)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_CANNOT_COMPLETE);
        }

        @Test
        void DELIVERED_order_transitions_to_COMPLETED_and_commits_stock() {
            Customer cust = customer(uuid(1));
            Order order = savedOrder(uuid(1), cust, OrderStatus.DELIVERED, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(any(UUID.class))).thenReturn(List.of());

            orderService.completeOrder(uuid(1));

            verify(orderRepository).save(argThat(o -> o.getStatus() == OrderStatus.COMPLETED));
            verify(inventoryService).completeOrder("ORDER", order.getOrderCode());
        }
    }

    // ─── getOrderById ─────────────────────────────────────────────────────────

    @Nested
    class GetOrderById {

        @Test
        void throws_ORDER_NOT_FOUND_when_order_belongs_to_different_customer() {
            Customer owner = customer(uuid(1));
            Customer other = customer(uuid(2));
            Order order = savedOrder(uuid(1), owner, OrderStatus.PENDING, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(uuid(1))).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.getOrderById(uuid(1), other))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        }
    }
}
