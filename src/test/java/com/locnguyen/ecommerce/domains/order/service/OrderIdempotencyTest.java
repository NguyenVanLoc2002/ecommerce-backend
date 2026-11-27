package com.locnguyen.ecommerce.domains.order.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.address.repository.AddressRepository;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.cart.entity.Cart;
import com.locnguyen.ecommerce.domains.cart.enums.CartStatus;
import com.locnguyen.ecommerce.domains.cart.repository.CartItemRepository;
import com.locnguyen.ecommerce.domains.cart.repository.CartRepository;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.idempotency.entity.IdempotencyKey;
import com.locnguyen.ecommerce.domains.idempotency.enums.IdempotencyStatus;
import com.locnguyen.ecommerce.domains.idempotency.service.IdempotencyService;
import com.locnguyen.ecommerce.domains.inventory.repository.InventoryRepository;
import com.locnguyen.ecommerce.domains.inventory.service.InventoryService;
import com.locnguyen.ecommerce.domains.notification.service.NotificationService;
import com.locnguyen.ecommerce.domains.order.dto.CreateOrderRequest;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.order.mapper.OrderMapper;
import com.locnguyen.ecommerce.domains.order.repository.OrderItemRepository;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.order.service.impl.OrderServiceImpl;
import com.locnguyen.ecommerce.domains.order.service.tax.OrderTaxEstimates;
import com.locnguyen.ecommerce.domains.payment.service.PaymentService;
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
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the idempotency wrapper around {@link OrderService#createOrder}.
 *
 * These tests mock {@link IdempotencyService} directly to verify that:
 * - A COMPLETED replay returns the existing order without re-executing checkout
 * - A PROCESSING response propagates IN_PROGRESS
 * - A CONFLICT propagates IDEMPOTENCY_KEY_CONFLICT
 * - The happy path calls markComplete after order creation
 * - The error path calls markFailed on AppException
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderIdempotencyTest {

    private static UUID uuid(long n) { return new UUID(0L, n); }

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
    @Mock IdempotencyService idempotencyService;

    @Mock OrderTaxEstimates orderTaxEstimates;
    @InjectMocks OrderServiceImpl orderService;

    private Customer customer;
    private CreateOrderRequest request;

    @BeforeEach
    void setUp() {
        customer = mock(Customer.class);
        when(customer.getId()).thenReturn(uuid(1));
        request = new CreateOrderRequest();
        request.setShippingAddressId(uuid(10));
    }

    private IdempotencyKey processingRecord() {
        IdempotencyKey k = new IdempotencyKey();
        ReflectionTestUtils.setField(k, "id", 1L);
        k.setStatus(IdempotencyStatus.PROCESSING);
        return k;
    }

    private IdempotencyKey completedRecord(UUID orderId) {
        IdempotencyKey k = new IdempotencyKey();
        ReflectionTestUtils.setField(k, "id", 1L);
        k.setStatus(IdempotencyStatus.COMPLETED);
        k.setResourceId(orderId.toString());
        return k;
    }

    // ─── Idempotency gate behavior ────────────────────────────────────────────

    @Nested
    class IdempotencyGate {

        @Test
        void replays_existing_order_when_COMPLETED_record_found() {
            IdempotencyKey completed = completedRecord(uuid(99));
            when(idempotencyService.findOrCreateProcessing(any(), any(), any(), any()))
                    .thenReturn(completed);

            Order existingOrder = new Order();
            ReflectionTestUtils.setField(existingOrder, "id", uuid(99));
            existingOrder.setOrderCode("ORD-EXISTING");
            existingOrder.setStatus(OrderStatus.PENDING);
            existingOrder.setPaymentMethod(PaymentMethod.COD);
            existingOrder.setPaymentStatus(PaymentStatus.PENDING);
            existingOrder.setSubTotal(BigDecimal.ZERO);
            existingOrder.setDiscountAmount(BigDecimal.ZERO);
            existingOrder.setShippingFee(BigDecimal.ZERO);
            existingOrder.setTotalAmount(BigDecimal.ZERO);
            existingOrder.setItems(new ArrayList<>());
            existingOrder.setCustomer(customer);
            when(orderRepository.findById(uuid(99))).thenReturn(Optional.of(existingOrder));

            orderService.createOrder(customer, request, "my-key");

            // Cart must NOT be touched (no checkout was executed)
            verify(cartRepository, never()).findByCustomerIdAndStatusWithLock(any(), any());
            // The existing order was fetched
            verify(orderRepository).findById(uuid(99));
        }

        @Test
        void throws_ORDER_NOT_FOUND_when_replay_order_missing() {
            IdempotencyKey completed = completedRecord(uuid(99));
            when(idempotencyService.findOrCreateProcessing(any(), any(), any(), any()))
                    .thenReturn(completed);
            when(orderRepository.findById(uuid(99))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createOrder(customer, request, "my-key"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        void propagates_IDEMPOTENCY_REQUEST_IN_PROGRESS_from_service() {
            when(idempotencyService.findOrCreateProcessing(any(), any(), any(), any()))
                    .thenThrow(new AppException(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS));

            assertThatThrownBy(() -> orderService.createOrder(customer, request, "my-key"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
        }

        @Test
        void propagates_IDEMPOTENCY_KEY_CONFLICT_from_service() {
            when(idempotencyService.findOrCreateProcessing(any(), any(), any(), any()))
                    .thenThrow(new AppException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));

            assertThatThrownBy(() -> orderService.createOrder(customer, request, "my-key"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        @Test
        void calls_markComplete_after_successful_checkout() {
            when(idempotencyService.findOrCreateProcessing(any(), any(), any(), any()))
                    .thenReturn(processingRecord());

            Cart cart = new Cart();
            ReflectionTestUtils.setField(cart, "id", uuid(5));
            cart.setCustomer(customer);
            cart.setStatus(CartStatus.ACTIVE);
            cart.setItems(new ArrayList<>());
            when(cartRepository.findByCustomerIdAndStatusWithLock(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cart));
            // Cart has no items → ORDER_EMPTY
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(uuid(5))).thenReturn(new ArrayList<>());

            // ORDER_EMPTY is an AppException → markFailed should be called
            assertThatThrownBy(() -> orderService.createOrder(customer, request, "my-key"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_EMPTY);

            verify(idempotencyService).markFailed(eq(1L), eq(ErrorCode.ORDER_EMPTY.getCode()));
        }

        @Test
        void calls_markFailed_on_AppException_during_checkout() {
            when(idempotencyService.findOrCreateProcessing(any(), any(), any(), any()))
                    .thenReturn(processingRecord());
            // No cart found → AppException(CART_NOT_FOUND)
            when(cartRepository.findByCustomerIdAndStatusWithLock(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createOrder(customer, request, "my-key"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CART_NOT_FOUND);

            verify(idempotencyService).markFailed(eq(1L), eq(ErrorCode.CART_NOT_FOUND.getCode()));
        }
    }
}
