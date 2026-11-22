package com.locnguyen.ecommerce.domains.order.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.inventory.service.InventoryService;
import com.locnguyen.ecommerce.domains.invoice.service.InvoiceService;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.entity.OrderItem;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.order.service.OrderHoldService;
import com.locnguyen.ecommerce.domains.payment.service.PaymentExpiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderHoldServiceImpl implements OrderHoldService {
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final InvoiceService invoiceService;
    private final AuditLogService auditLogService;
    private final PaymentExpiryService paymentExpiryService;

    @Override
    @Transactional
    public void expireOrder(String orderCode) {
        Order order = orderRepository.findByOrderCodeWithLock(orderCode).orElse(null);
        if (order == null) return;
        boolean awaitingOnline = order.getPaymentMethod() == PaymentMethod.ONLINE
                && order.getStatus() == OrderStatus.AWAITING_PAYMENT;
        boolean awaitingCod = order.getPaymentMethod() == PaymentMethod.COD
                && order.getStatus() == OrderStatus.PENDING;
        if (awaitingOnline && order.getPaymentStatus() == PaymentStatus.PAID) {
            LocalDateTime expiry = inventoryService.getOrderHoldExpiry(orderCode);
            if (expiry == null && inventoryService.hasCompleteOrderHold(
                    orderCode, quantities(order), LocalDateTime.now())) return;
            if (expiry == null || order.getPaidAt() == null || order.getPaidAt().isAfter(expiry)
                    || !inventoryService.makeOrderHoldDurable(orderCode, quantities(order), null)) {
                cancelExpired(order);
            }
        } else if (awaitingOnline || awaitingCod) {
            if (!inventoryService.hasCompleteOrderHold(orderCode, quantities(order), LocalDateTime.now())) {
                cancelExpired(order);
            }
        } else if (!order.getStatus().isTerminal()) {
            // Older confirmed orders can still carry a checkout expiry; retain their existing hold.
            if (!inventoryService.makeOrderHoldDurable(orderCode, quantities(order), null)) {
                log.error("Active order has incomplete stock hold: orderCode={}", orderCode);
            }
        } else {
            inventoryService.releaseStock("ORDER", orderCode);
        }
    }

    @Override
    @Transactional
    public boolean acceptOnlineSuccess(Order order) {
        if (order.getStatus() == OrderStatus.CANCELLED) return false;
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT
                || !inventoryService.makeOrderHoldDurable(order.getOrderCode(), quantities(order), LocalDateTime.now())) {
            if (order.getStatus() == OrderStatus.AWAITING_PAYMENT) cancelExpired(order);
            return false;
        }
        return true;
    }

    @Override
    @Transactional
    public void retainForFulfillment(Order order) {
        if (!inventoryService.makeOrderHoldDurable(order.getOrderCode(), quantities(order), LocalDateTime.now())) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID, "Order stock hold has expired or is incomplete");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void requireFulfillmentHold(Order order) {
        if (!inventoryService.hasCompleteOrderHold(order.getOrderCode(), quantities(order), LocalDateTime.now())) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID, "Order stock hold is unavailable");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public LocalDateTime getCheckoutExpiry(Order order) {
        return inventoryService.getOrderHoldExpiry(order.getOrderCode());
    }

    private void cancelExpired(Order order) {
        order.setStatus(OrderStatus.CANCELLED);
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            order.setPaymentStatus(PaymentStatus.CANCELLED);
            paymentExpiryService.cancelUnpaidPayment(order.getId());
        }
        orderRepository.save(order);
        inventoryService.releaseStock("ORDER", order.getOrderCode());
        invoiceService.voidByOrderId(order.getId(), "Checkout stock hold expired");
        auditLogService.log(AuditAction.ORDER_CANCELLED, "ORDER", order.getOrderCode(),
                "Checkout stock hold expired");
    }

    private Map<UUID, Integer> quantities(Order order) {
        return order.getItems().stream().collect(Collectors.groupingBy(
                item -> item.getVariant().getId(), Collectors.summingInt(OrderItem::getQuantity)));
    }
}
