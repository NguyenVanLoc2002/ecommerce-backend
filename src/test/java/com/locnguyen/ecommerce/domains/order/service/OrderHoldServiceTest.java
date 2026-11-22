package com.locnguyen.ecommerce.domains.order.service;

import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.inventory.service.InventoryService;
import com.locnguyen.ecommerce.domains.invoice.service.InvoiceService;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.entity.OrderItem;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.order.service.impl.OrderHoldServiceImpl;
import com.locnguyen.ecommerce.domains.payment.service.PaymentExpiryService;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderHoldServiceTest {
    @Mock OrderRepository orderRepository;
    @Mock InventoryService inventoryService;
    @Mock InvoiceService invoiceService;
    @Mock AuditLogService auditLogService;
    @Mock PaymentExpiryService paymentExpiryService;
    @InjectMocks OrderHoldServiceImpl service;

    private Order order;
    private UUID variantId;

    @BeforeEach
    void setUp() {
        variantId = UUID.randomUUID();
        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", variantId);
        OrderItem item = new OrderItem();
        item.setVariant(variant);
        item.setQuantity(2);
        order = new Order();
        order.setOrderCode("ORD-HOLD");
        order.setPaymentMethod(PaymentMethod.ONLINE);
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.getItems().add(item);
    }

    @Test
    void unpaidExpiredOrderIsCancelledAndReleased() {
        when(orderRepository.findByOrderCodeWithLock("ORD-HOLD")).thenReturn(Optional.of(order));
        service.expireOrder("ORD-HOLD");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(paymentExpiryService).cancelUnpaidPayment(order.getId());
        verify(inventoryService).releaseStock("ORDER", "ORD-HOLD");
    }

    @Test
    void successfulPaymentBeforeDeadlineKeepsStockForFulfillment() {
        when(inventoryService.makeOrderHoldDurable(eq("ORD-HOLD"), eq(Map.of(variantId, 2)), any()))
                .thenReturn(true);
        assertThat(service.acceptOnlineSuccess(order)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        verify(inventoryService, never()).releaseStock(anyString(), anyString());
    }

    @Test
    void lateSuccessCancelsOrderBeforeRecordingPaidPayment() {
        assertThat(service.acceptOnlineSuccess(order)).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryService).releaseStock("ORDER", "ORD-HOLD");
    }

    @Test
    void confirmedCodOrderRetainsExistingHoldInsteadOfExpiring() {
        order.setPaymentMethod(PaymentMethod.COD);
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findByOrderCodeWithLock("ORD-HOLD")).thenReturn(Optional.of(order));
        service.expireOrder("ORD-HOLD");
        verify(inventoryService).makeOrderHoldDurable("ORD-HOLD", Map.of(variantId, 2), null);
        verify(inventoryService, never()).releaseStock(anyString(), anyString());
    }

    @Test
    void unconfirmedCodOrderExpiresAfterItsOwnWaitingPeriod() {
        order.setPaymentMethod(PaymentMethod.COD);
        order.setStatus(OrderStatus.PENDING);
        when(orderRepository.findByOrderCodeWithLock("ORD-HOLD")).thenReturn(Optional.of(order));
        service.expireOrder("ORD-HOLD");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryService).releaseStock("ORDER", "ORD-HOLD");
    }

    @Test
    void staleSchedulerScanDoesNotCancelAlreadyPaidDurableOrder() {
        order.setPaymentStatus(PaymentStatus.PAID);
        when(orderRepository.findByOrderCodeWithLock("ORD-HOLD")).thenReturn(Optional.of(order));
        when(inventoryService.hasCompleteOrderHold(eq("ORD-HOLD"), eq(Map.of(variantId, 2)), any()))
                .thenReturn(true);
        service.expireOrder("ORD-HOLD");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        verify(inventoryService, never()).releaseStock(anyString(), anyString());
    }
}
