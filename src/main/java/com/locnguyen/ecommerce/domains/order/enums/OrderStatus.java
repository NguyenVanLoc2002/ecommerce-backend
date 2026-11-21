package com.locnguyen.ecommerce.domains.order.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.locnguyen.ecommerce.domains.order.enums.OrderStatus.*;

/**
 * Order status with strict state machine transitions.
 *
 * <p>Valid transitions:
 * <pre>
 *   PENDING          → AWAITING_PAYMENT, CONFIRMED (COD), CANCELLED
 *   AWAITING_PAYMENT → CONFIRMED, CANCELLED
 *   CONFIRMED        → PROCESSING, CANCELLED
 *   PROCESSING       → SHIPPED
 *   SHIPPED          → DELIVERED
 *   DELIVERED        → COMPLETED
 *   COMPLETED        → REFUNDED
 * </pre>
 */
public enum OrderStatus {

    PENDING,
    AWAITING_PAYMENT,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    COMPLETED,
    CANCELLED,
    REFUNDED;

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            PENDING,          EnumSet.of(AWAITING_PAYMENT, CONFIRMED, CANCELLED),
            AWAITING_PAYMENT, EnumSet.of(CONFIRMED, CANCELLED),
            CONFIRMED,        EnumSet.of(PROCESSING, CANCELLED),
            PROCESSING,       EnumSet.of(SHIPPED),
            SHIPPED,          EnumSet.of(DELIVERED),
            DELIVERED,        EnumSet.of(COMPLETED),
            COMPLETED,        EnumSet.of(REFUNDED)
            // CANCELLED and REFUNDED are terminal — no outgoing transitions
    );

    /**
     * Check if transitioning from {@code this} to {@code target} is valid.
     */
    public boolean canTransitionTo(OrderStatus target) {
        Set<OrderStatus> allowed = TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    /**
     * Terminal statuses that cannot be transitioned out of.
     */
    public boolean isTerminal() {
        return this == CANCELLED || this == REFUNDED;
    }
}
