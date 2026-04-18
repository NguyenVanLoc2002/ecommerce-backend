package com.locnguyen.ecommerce.domains.order.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.address.entity.Address;
import com.locnguyen.ecommerce.domains.address.repository.AddressRepository;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.cart.entity.Cart;
import com.locnguyen.ecommerce.domains.cart.entity.CartItem;
import com.locnguyen.ecommerce.domains.cart.enums.CartStatus;
import com.locnguyen.ecommerce.domains.cart.repository.CartItemRepository;
import com.locnguyen.ecommerce.domains.cart.repository.CartRepository;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.inventory.entity.Inventory;
import com.locnguyen.ecommerce.domains.inventory.entity.Warehouse;
import com.locnguyen.ecommerce.domains.inventory.repository.InventoryRepository;
import com.locnguyen.ecommerce.domains.inventory.service.InventoryService;
import com.locnguyen.ecommerce.domains.order.dto.CreateOrderRequest;
import com.locnguyen.ecommerce.domains.order.dto.OrderResponse;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.entity.OrderItem;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.order.mapper.OrderMapper;
import com.locnguyen.ecommerce.domains.order.repository.OrderItemRepository;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.notification.service.NotificationService;
import com.locnguyen.ecommerce.domains.payment.service.PaymentService;
import com.locnguyen.ecommerce.domains.product.entity.Product;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock OrderMapper orderMapper;
    @Mock CartRepository cartRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock AddressRepository addressRepository;
    @Mock InventoryService inventoryService;
    @Mock InventoryRepository inventoryRepository;
    @Mock PaymentService paymentService;
    @Mock AuditLogService auditLogService;
    @Mock NotificationService notificationService;

    @InjectMocks OrderService orderService;

    // ─── factories ───────────────────────────────────────────────────────────

    private Customer customer(long id) {
        Customer c = mock(Customer.class);
        when(c.getId()).thenReturn(id);
        return c;
    }

    private Cart activeCart(long cartId, Customer customer) {
        Cart cart = new Cart();
        setId(cart, cartId);
        cart.setCustomer(customer);
        cart.setStatus(CartStatus.ACTIVE);
        cart.setItems(new ArrayList<>());
        return cart;
    }

    private Address address(long id, Customer owner) {
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

    private ProductVariant variant(long id, BigDecimal basePrice, BigDecimal salePrice) {
        Product product = new Product();
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
        setId(ci, 100L + variant.getId());
        ci.setCart(cart);
        ci.setVariant(variant);
        ci.setQuantity(quantity);
        return ci;
    }

    private Inventory inventory(long id, ProductVariant variant, int onHand, int reserved) {
        Warehouse warehouse = new Warehouse();
        setId(warehouse, 1L);

        Inventory inv = new Inventory();
        setId(inv, id);
        inv.setVariant(variant);
        inv.setWarehouse(warehouse);
        inv.setOnHand(onHand);
        inv.setReserved(reserved);
        return inv;
    }

    private Order savedOrder(long id, Customer customer, OrderStatus status,
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

    private CreateOrderRequest createRequest(Long addressId, String paymentMethod) {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setShippingAddressId(addressId);
        req.setPaymentMethod(paymentMethod);
        return req;
    }

    private static void setId(Object entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }

    // ─── setupCommonStubsForCreateOrder ─────────────────────────────────────

    @BeforeEach
    void stubOrderRepositorySave() {
        // orderRepository.save() returns whatever order is passed in (adds an id)
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) setId(o, 99L);
            return o;
        });
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── createOrder ─────────────────────────────────────────────────────────

    @Nested
    class CreateOrder {

        @Test
        void throws_CART_NOT_FOUND_when_no_active_cart() {
            Customer cust = customer(1L);
            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createOrder(cust, createRequest(10L, null)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CART_NOT_FOUND);
        }

        @Test
        void throws_ORDER_EMPTY_when_cart_has_no_items() {
            Customer cust = customer(1L);
            Cart cart = activeCart(5L, cust);
            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(5L)).thenReturn(List.of());

            assertThatThrownBy(() -> orderService.createOrder(cust, createRequest(10L, null)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_EMPTY);
        }

        @Test
        void throws_ADDRESS_NOT_FOUND_when_address_missing() {
            Customer cust = customer(1L);
            Cart cart = activeCart(5L, cust);
            ProductVariant v = variant(1L, new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 2);

            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(5L)).thenReturn(List.of(ci));
            when(addressRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createOrder(cust, createRequest(99L, null)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ADDRESS_NOT_FOUND);
        }

        @Test
        void throws_ADDRESS_NOT_FOUND_when_address_belongs_to_another_customer() {
            Customer cust = customer(1L);
            Customer otherCustomer = customer(999L);
            Cart cart = activeCart(5L, cust);
            ProductVariant v = variant(1L, new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 2);
            Address addr = address(10L, otherCustomer);  // different owner

            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(5L)).thenReturn(List.of(ci));
            when(addressRepository.findById(10L)).thenReturn(Optional.of(addr));

            assertThatThrownBy(() -> orderService.createOrder(cust, createRequest(10L, null)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ADDRESS_NOT_FOUND);
        }

        @Test
        void throws_INVENTORY_NOT_FOUND_when_no_inventory_for_variant() {
            Customer cust = customer(1L);
            Cart cart = activeCart(5L, cust);
            ProductVariant v = variant(1L, new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 2);
            Address addr = address(10L, cust);

            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(5L)).thenReturn(List.of(ci));
            when(addressRepository.findById(10L)).thenReturn(Optional.of(addr));
            when(inventoryRepository.findByVariantIdIn(List.of(1L))).thenReturn(List.of());

            assertThatThrownBy(() -> orderService.createOrder(cust, createRequest(10L, null)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVENTORY_NOT_FOUND);
        }

        @Test
        void cod_order_starts_in_PENDING_status() {
            Customer cust = customer(1L);
            Cart cart = activeCart(5L, cust);
            ProductVariant v = variant(1L, new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 1);
            Address addr = address(10L, cust);
            Inventory inv = inventory(20L, v, 10, 0);

            stubSuccessfulCreateOrder(cust, cart, List.of(ci), addr, List.of(inv));

            orderService.createOrder(cust, createRequest(10L, "COD"));

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(captor.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.COD);
        }

        @Test
        void online_order_starts_in_AWAITING_PAYMENT_status() {
            Customer cust = customer(1L);
            Cart cart = activeCart(5L, cust);
            ProductVariant v = variant(1L, new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 1);
            Address addr = address(10L, cust);
            Inventory inv = inventory(20L, v, 10, 0);

            stubSuccessfulCreateOrder(cust, cart, List.of(ci), addr, List.of(inv));

            orderService.createOrder(cust, createRequest(10L, "ONLINE"));

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
            assertThat(captor.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.ONLINE);
        }

        @Test
        void cod_order_creates_cod_payment_record() {
            Customer cust = customer(1L);
            Cart cart = activeCart(5L, cust);
            ProductVariant v = variant(1L, new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 1);
            Address addr = address(10L, cust);
            Inventory inv = inventory(20L, v, 10, 0);

            stubSuccessfulCreateOrder(cust, cart, List.of(ci), addr, List.of(inv));

            orderService.createOrder(cust, createRequest(10L, "COD"));

            verify(paymentService).createCodPayment(any(Order.class));
        }

        @Test
        void online_order_does_NOT_create_cod_payment() {
            Customer cust = customer(1L);
            Cart cart = activeCart(5L, cust);
            ProductVariant v = variant(1L, new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 1);
            Address addr = address(10L, cust);
            Inventory inv = inventory(20L, v, 10, 0);

            stubSuccessfulCreateOrder(cust, cart, List.of(ci), addr, List.of(inv));

            orderService.createOrder(cust, createRequest(10L, "ONLINE"));

            verify(paymentService, never()).createCodPayment(any());
        }

        @Test
        void cart_is_marked_CHECKED_OUT_after_order_creation() {
            Customer cust = customer(1L);
            Cart cart = activeCart(5L, cust);
            ProductVariant v = variant(1L, new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 1);
            Address addr = address(10L, cust);
            Inventory inv = inventory(20L, v, 10, 0);

            stubSuccessfulCreateOrder(cust, cart, List.of(ci), addr, List.of(inv));

            orderService.createOrder(cust, createRequest(10L, "COD"));

            verify(cartRepository).save(argThat(c -> c.getStatus() == CartStatus.CHECKED_OUT));
        }

        @Test
        void subTotal_uses_salePrice_when_set() {
            // Variant has basePrice=200,000 and salePrice=150,000
            // Expected subTotal = 150,000 * 2 = 300,000
            Customer cust = customer(1L);
            Cart cart = activeCart(5L, cust);
            ProductVariant v = variant(1L, new BigDecimal("200000"), new BigDecimal("150000"));
            CartItem ci = cartItem(cart, v, 2);
            Address addr = address(10L, cust);
            Inventory inv = inventory(20L, v, 10, 0);

            stubSuccessfulCreateOrder(cust, cart, List.of(ci), addr, List.of(inv));

            orderService.createOrder(cust, createRequest(10L, "COD"));

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getSubTotal()).isEqualByComparingTo("300000");
            assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("300000");
        }

        @Test
        void subTotal_uses_basePrice_when_salePrice_is_null() {
            // Variant has basePrice=100,000 and no salePrice
            Customer cust = customer(1L);
            Cart cart = activeCart(5L, cust);
            ProductVariant v = variant(1L, new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 3);
            Address addr = address(10L, cust);
            Inventory inv = inventory(20L, v, 10, 0);

            stubSuccessfulCreateOrder(cust, cart, List.of(ci), addr, List.of(inv));

            orderService.createOrder(cust, createRequest(10L, "COD"));

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getSubTotal()).isEqualByComparingTo("300000");
        }

        @Test
        void shipping_address_fields_are_snapshotted_from_address_entity() {
            Customer cust = customer(1L);
            Cart cart = activeCart(5L, cust);
            ProductVariant v = variant(1L, new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 1);
            Address addr = address(10L, cust);  // fields set in factory
            Inventory inv = inventory(20L, v, 5, 0);

            stubSuccessfulCreateOrder(cust, cart, List.of(ci), addr, List.of(inv));

            orderService.createOrder(cust, createRequest(10L, "COD"));

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            Order saved = captor.getValue();
            assertThat(saved.getReceiverName()).isEqualTo("Nguyen Van A");
            assertThat(saved.getReceiverPhone()).isEqualTo("0901234567");
            assertThat(saved.getShippingCity()).isEqualTo("Ho Chi Minh");
        }

        @Test
        void picks_warehouse_with_most_available_stock() {
            Customer cust = customer(1L);
            Cart cart = activeCart(5L, cust);
            ProductVariant v = variant(1L, new BigDecimal("100000"), null);
            CartItem ci = cartItem(cart, v, 1);
            Address addr = address(10L, cust);

            // Warehouse A: onHand=5, reserved=3 → available=2
            Inventory invA = inventory(20L, v, 5, 3);
            setId(invA.getWarehouse(), 1L);

            // Warehouse B: onHand=20, reserved=2 → available=18 ← best
            Inventory invB = inventory(21L, v, 20, 2);
            setId(invB.getWarehouse(), 2L);

            stubSuccessfulCreateOrder(cust, cart, List.of(ci), addr, List.of(invA, invB));

            orderService.createOrder(cust, createRequest(10L, "COD"));

            // Should reserve from warehouse 2 (available=18 > available=2)
            verify(inventoryService).reserveStock(argThat(req -> req.getWarehouseId() == 2L));
        }

        @Test
        void reserve_is_called_once_per_cart_item() {
            Customer cust = customer(1L);
            Cart cart = activeCart(5L, cust);
            ProductVariant v1 = variant(1L, new BigDecimal("100000"), null);
            ProductVariant v2 = variant(2L, new BigDecimal("200000"), null);
            CartItem ci1 = cartItem(cart, v1, 1);
            CartItem ci2 = cartItem(cart, v2, 2);
            Address addr = address(10L, cust);
            Inventory inv1 = inventory(20L, v1, 10, 0);
            Inventory inv2 = inventory(21L, v2, 10, 0);

            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(5L))
                    .thenReturn(List.of(ci1, ci2));
            when(addressRepository.findById(10L)).thenReturn(Optional.of(addr));
            when(inventoryRepository.findByVariantIdIn(List.of(1L, 2L)))
                    .thenReturn(List.of(inv1, inv2));
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(anyLong()))
                    .thenReturn(List.of());

            orderService.createOrder(cust, createRequest(10L, "COD"));

            verify(inventoryService, times(2)).reserveStock(any());
        }

        // helper: stubs all the happy-path repository calls for a single-item order
        private void stubSuccessfulCreateOrder(Customer cust, Cart cart, List<CartItem> items,
                                                Address addr, List<Inventory> inventories) {
            when(cartRepository.findByCustomerIdAndStatus(cust.getId(), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId()))
                    .thenReturn(items);
            when(addressRepository.findById(addr.getId())).thenReturn(Optional.of(addr));
            List<Long> variantIds = items.stream()
                    .map(ci -> ci.getVariant().getId())
                    .toList();
            when(inventoryRepository.findByVariantIdIn(variantIds)).thenReturn(inventories);
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(anyLong()))
                    .thenReturn(List.of());
        }
    }

    // ─── confirmOrder ─────────────────────────────────────────────────────────

    @Nested
    class ConfirmOrder {

        @Test
        void throws_ORDER_NOT_FOUND_when_order_does_not_exist() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.confirmOrder(99L))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        void throws_ORDER_STATUS_INVALID_when_order_is_already_PROCESSING() {
            Customer cust = customer(1L);
            Order order = savedOrder(1L, cust, OrderStatus.PROCESSING, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.confirmOrder(1L))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_STATUS_INVALID);
        }

        @Test
        void throws_ORDER_STATUS_INVALID_when_online_payment_not_paid() {
            Customer cust = customer(1L);
            Order order = savedOrder(1L, cust, OrderStatus.AWAITING_PAYMENT,
                    PaymentMethod.ONLINE, new BigDecimal("100000"));
            order.setPaymentStatus(PaymentStatus.PENDING);  // not PAID yet
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.confirmOrder(1L))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_STATUS_INVALID);
        }

        @Test
        void throws_ORDER_STATUS_INVALID_when_COD_order_is_in_PENDING_state() {
            // State machine: PENDING → {AWAITING_PAYMENT, CANCELLED}
            // PENDING → CONFIRMED is NOT a valid transition; COD must go through AWAITING_PAYMENT first
            Customer cust = customer(1L);
            Order order = savedOrder(1L, cust, OrderStatus.PENDING, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.confirmOrder(1L))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_STATUS_INVALID);
        }

        @Test
        void cod_AWAITING_PAYMENT_order_can_be_confirmed_without_payment_status_check() {
            Customer cust = customer(1L);
            Order order = savedOrder(1L, cust, OrderStatus.AWAITING_PAYMENT, PaymentMethod.COD,
                    new BigDecimal("100000"));
            order.setPaymentStatus(PaymentStatus.PENDING);  // COD — not paid yet, but still confirming
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(anyLong())).thenReturn(List.of());

            orderService.confirmOrder(1L);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        void online_order_can_be_confirmed_when_payment_is_PAID() {
            Customer cust = customer(1L);
            Order order = savedOrder(1L, cust, OrderStatus.AWAITING_PAYMENT,
                    PaymentMethod.ONLINE, new BigDecimal("100000"));
            order.setPaymentStatus(PaymentStatus.PAID);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(anyLong())).thenReturn(List.of());

            orderService.confirmOrder(1L);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }
    }

    // ─── cancelOrder (admin) ─────────────────────────────────────────────────

    @Nested
    class CancelOrder {

        @Test
        void admin_can_cancel_CONFIRMED_order() {
            Customer cust = customer(1L);
            Order order = savedOrder(1L, cust, OrderStatus.CONFIRMED, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(anyLong())).thenReturn(List.of());

            orderService.cancelOrder(1L);

            verify(orderRepository).save(argThat(o -> o.getStatus() == OrderStatus.CANCELLED));
            verify(inventoryService).releaseStock("ORDER", order.getOrderCode());
        }

        @Test
        void throws_ORDER_CANNOT_CANCEL_when_order_is_SHIPPED() {
            Customer cust = customer(1L);
            Order order = savedOrder(1L, cust, OrderStatus.SHIPPED, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(1L))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_CANNOT_CANCEL);
        }

        @Test
        void throws_ORDER_CANNOT_CANCEL_when_order_is_already_CANCELLED() {
            Customer cust = customer(1L);
            Order order = savedOrder(1L, cust, OrderStatus.CANCELLED, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(1L))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_CANNOT_CANCEL);
        }

        @Test
        void releasing_stock_is_called_on_successful_cancel() {
            Customer cust = customer(1L);
            Order order = savedOrder(1L, cust, OrderStatus.PENDING, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(anyLong())).thenReturn(List.of());

            orderService.cancelOrder(1L);

            verify(inventoryService).releaseStock("ORDER", "ORD202604060001");
        }
    }

    // ─── cancelMyOrder (customer) ────────────────────────────────────────────

    @Nested
    class CancelMyOrder {

        @Test
        void customer_can_cancel_PENDING_order() {
            Customer cust = customer(1L);
            Order order = savedOrder(1L, cust, OrderStatus.PENDING, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(anyLong())).thenReturn(List.of());

            orderService.cancelMyOrder(1L, cust);

            verify(orderRepository).save(argThat(o -> o.getStatus() == OrderStatus.CANCELLED));
        }

        @Test
        void customer_can_cancel_AWAITING_PAYMENT_order() {
            Customer cust = customer(1L);
            Order order = savedOrder(1L, cust, OrderStatus.AWAITING_PAYMENT, PaymentMethod.ONLINE,
                    new BigDecimal("100000"));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(anyLong())).thenReturn(List.of());

            orderService.cancelMyOrder(1L, cust);

            verify(orderRepository).save(argThat(o -> o.getStatus() == OrderStatus.CANCELLED));
        }

        @Test
        void customer_cannot_cancel_CONFIRMED_order() {
            Customer cust = customer(1L);
            Order order = savedOrder(1L, cust, OrderStatus.CONFIRMED, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelMyOrder(1L, cust))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_CANNOT_CANCEL);
        }

        @Test
        void throws_ORDER_NOT_FOUND_when_order_belongs_to_another_customer() {
            Customer cust = customer(1L);
            Customer otherCust = customer(999L);
            Order order = savedOrder(1L, otherCust, OrderStatus.PENDING, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelMyOrder(1L, cust))
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
            Customer cust = customer(1L);
            Order order = savedOrder(1L, cust, OrderStatus.CONFIRMED, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.completeOrder(1L))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_CANNOT_COMPLETE);
        }

        @Test
        void DELIVERED_order_transitions_to_COMPLETED_and_commits_stock() {
            Customer cust = customer(1L);
            Order order = savedOrder(1L, cust, OrderStatus.DELIVERED, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(anyLong())).thenReturn(List.of());

            orderService.completeOrder(1L);

            verify(orderRepository).save(argThat(o -> o.getStatus() == OrderStatus.COMPLETED));
            verify(inventoryService).completeOrder("ORDER", order.getOrderCode());
        }
    }

    // ─── getOrderById ─────────────────────────────────────────────────────────

    @Nested
    class GetOrderById {

        @Test
        void throws_ORDER_NOT_FOUND_when_order_belongs_to_different_customer() {
            Customer owner = customer(1L);
            Customer other = customer(2L);
            Order order = savedOrder(1L, owner, OrderStatus.PENDING, PaymentMethod.COD,
                    new BigDecimal("100000"));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.getOrderById(1L, other))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        }
    }
}
