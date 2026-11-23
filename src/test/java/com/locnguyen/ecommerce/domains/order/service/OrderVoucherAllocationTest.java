package com.locnguyen.ecommerce.domains.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.entity.OrderItem;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderVoucherAllocationTest {

    @Test
    void resolve_whenLegacyOrderHasRemainder_allocatesByWeightAndStableSku() {
        Order order = new Order();
        order.setDiscountAmount(new BigDecimal("5"));
        OrderItem first = line("A", "10.00");
        OrderItem second = line("B", "10.00");
        OrderItem third = line("C", "20.00");
        order.getItems().add(third);
        order.getItems().add(second);
        order.getItems().add(first);

        assertEquals(Map.of(first, new BigDecimal("1"),
                second, new BigDecimal("1"), third, new BigDecimal("3")),
                OrderVoucherAllocation.resolve(order, Set.copyOf(order.getItems())));

        order.setDiscountAmount(new BigDecimal("1"));
        order.getItems().remove(third);
        assertEquals(Map.of(first, new BigDecimal("1"), second, new BigDecimal("0")),
                OrderVoucherAllocation.resolve(order, Set.copyOf(order.getItems())));
    }

    @Test
    void resolve_whenSnapshotExists_usesStoredAllocationAndRejectsIncompleteSnapshot() {
        Order order = new Order();
        order.setDiscountAmount(new BigDecimal("10.00"));
        OrderItem first = line("A", "10.00");
        OrderItem second = line("B", "10.00");
        first.setVoucherDiscountAmount(new BigDecimal("3.00"));
        second.setVoucherDiscountAmount(new BigDecimal("7.00"));
        order.getItems().add(first);
        order.getItems().add(second);

        assertEquals(Map.of(first, new BigDecimal("3.00"), second, new BigDecimal("7.00")),
                OrderVoucherAllocation.resolve(order, Set.copyOf(order.getItems())));

        second.setVoucherDiscountAmount(null);
        assertThrows(IllegalStateException.class, () -> OrderVoucherAllocation.resolve(order, Set.copyOf(order.getItems())));
    }

    @Test
    void resolve_whenOnlyOneLineIsEligible_allocatesOnlyToThatLine() {
        Order order = new Order();
        order.setDiscountAmount(new BigDecimal("5"));
        OrderItem eligible = line("A", "10.00");
        OrderItem excluded = line("B", "20.00");
        order.getItems().addAll(Set.of(eligible, excluded));

        assertEquals(Map.of(eligible, new BigDecimal("5"), excluded, BigDecimal.ZERO),
                OrderVoucherAllocation.resolve(order, Set.of(eligible)));
        assertThrows(IllegalStateException.class, () -> OrderVoucherAllocation.resolve(order, Set.of()));
    }

    private OrderItem line(String sku, String total) {
        OrderItem item = new OrderItem();
        item.setSku(sku);
        item.setLineTotal(new BigDecimal(total));
        return item;
    }
}
