package com.locnguyen.ecommerce.domains.order.service;

import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.entity.OrderItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OrderVoucherAllocation {
    private static final BigDecimal MINOR_UNIT = BigDecimal.ONE;

    private OrderVoucherAllocation() {}

    public static Map<OrderItem, BigDecimal> resolve(Order order, Set<OrderItem> verifiedEligibleItems) {
        List<OrderItem> items = order.getItems();
        BigDecimal discount = order.getDiscountAmount().setScale(0, RoundingMode.UNNECESSARY);
        Map<OrderItem, BigDecimal> allocation = new LinkedHashMap<>();
        if (items.stream().anyMatch(item -> item.getVoucherDiscountAmount() != null)) {
            BigDecimal allocated = BigDecimal.ZERO;
            for (OrderItem item : items) {
                BigDecimal stored = item.getVoucherDiscountAmount();
                if (stored == null || stored.stripTrailingZeros().scale() > 0 || stored.signum() < 0 || stored.compareTo(item.getLineTotal()) > 0) {
                    throw new IllegalStateException("Invalid order item voucher snapshot");
                }
                allocation.put(item, stored);
                allocated = allocated.add(stored);
            }
            if (allocated.compareTo(discount) != 0) {
                throw new IllegalStateException("Voucher snapshots do not match order discount");
            }
            return allocation;
        }

        if (discount.signum() == 0) {
            items.forEach(item -> allocation.put(item, BigDecimal.ZERO));
            return allocation;
        }
        if (verifiedEligibleItems == null || verifiedEligibleItems.isEmpty()
                || !items.containsAll(verifiedEligibleItems)) {
            throw new IllegalStateException("Voucher scope requires historical evidence");
        }

        BigDecimal subtotal = verifiedEligibleItems.stream().map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (discount.signum() < 0 || discount.compareTo(subtotal) > 0
                || items.stream().anyMatch(item -> item.getLineTotal().signum() < 0)) {
            throw new IllegalStateException("Invalid order totals for voucher allocation");
        }

        List<Share> shares = new ArrayList<>();
        BigDecimal unallocated = discount;
        for (OrderItem item : items) {
            if (!verifiedEligibleItems.contains(item)) {
                allocation.put(item, BigDecimal.ZERO);
                continue;
            }
            BigDecimal weighted = discount.multiply(item.getLineTotal());
            BigDecimal lineMaximum = item.getLineTotal().setScale(0, RoundingMode.DOWN);
            BigDecimal amount = weighted.divide(subtotal, 0, RoundingMode.DOWN).min(lineMaximum);
            allocation.put(item, amount);
            if (amount.compareTo(lineMaximum) < 0) {
                shares.add(new Share(item, weighted.subtract(amount.multiply(subtotal))));
            }
            unallocated = unallocated.subtract(amount);
        }

        // Allocate each remaining minor unit to the largest proportional remainder.
        shares.sort(Comparator.comparing(Share::remainder).reversed()
                .thenComparing(share -> share.item().getSku(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(share -> share.item().getId(), Comparator.nullsLast(Comparator.naturalOrder())));
        int extraUnits = unallocated.min(BigDecimal.valueOf(shares.size())).intValueExact();
        for (Share share : shares.subList(0, extraUnits)) {
            allocation.compute(share.item(), (item, amount) -> amount.add(MINOR_UNIT));
        }

        // Fractional historical line totals can leave some lines with no whole-VND capacity.
        unallocated = unallocated.subtract(BigDecimal.valueOf(extraUnits));
        for (Share share : shares) {
            if (unallocated.signum() == 0) {
                break;
            }
            OrderItem item = share.item();
            BigDecimal current = allocation.get(item);
            BigDecimal capacity = item.getLineTotal().setScale(0, RoundingMode.DOWN).subtract(current);
            BigDecimal increment = unallocated.min(capacity);
            allocation.put(item, current.add(increment));
            unallocated = unallocated.subtract(increment);
        }
        if (unallocated.signum() != 0) {
            throw new IllegalStateException("Voucher discount cannot be allocated within eligible line amounts");
        }
        return allocation;
    }

    private record Share(OrderItem item, BigDecimal remainder) {}
}
