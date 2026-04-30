package com.locnguyen.ecommerce.domains.shipment.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle status of a shipment, with a strict state machine.
 *
 * <pre>
 * PENDING ──► IN_TRANSIT ──► OUT_FOR_DELIVERY ──► DELIVERED
 *                  │                   │
 *                  └──────► FAILED ◄───┘
 *                               │
 *                               ▼
 *                           RETURNED
 * </pre>
 */
public enum ShipmentStatus {

    /** Created, awaiting carrier pickup. */
    PENDING,

    /** Carrier is picking up the package. */
    PICKING,

    /** Picked up by carrier and in transit. */
    IN_TRANSIT,

    /** Out for final delivery. */
    OUT_FOR_DELIVERY,

    /** Successfully delivered to customer. Terminal. */
    DELIVERED,

    /** Delivery attempt failed. */
    FAILED,

    /** Package returned to sender. Terminal. */
    RETURNED;

    private static final Map<ShipmentStatus, Set<ShipmentStatus>> TRANSITIONS = Map.of(
            PENDING,           EnumSet.of(PICKING, FAILED),
            PICKING,           EnumSet.of(IN_TRANSIT, FAILED),
            IN_TRANSIT,        EnumSet.of(OUT_FOR_DELIVERY, FAILED),
            OUT_FOR_DELIVERY,  EnumSet.of(DELIVERED, FAILED),
            FAILED,            EnumSet.of(RETURNED)
            // DELIVERED and RETURNED are terminal
    );

    public boolean canTransitionTo(ShipmentStatus target) {
        Set<ShipmentStatus> allowed = TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == RETURNED;
    }
}
