package com.locnguyen.ecommerce.domains.returns.enums;

import java.util.Set;

public enum ReturnStatus {
    PENDING_DELIVERY_VERIFICATION,
    REQUESTED,
    APPROVED,
    RECEIVED,
    INSPECTED,
    COMPLETED,
    REJECTED,
    CANCELLED;

    public boolean canTransitionTo(ReturnStatus next) {
        return switch (this) {
            case PENDING_DELIVERY_VERIFICATION -> Set.of(REQUESTED, REJECTED, CANCELLED).contains(next);
            case REQUESTED -> Set.of(APPROVED, REJECTED, CANCELLED).contains(next);
            case APPROVED -> Set.of(RECEIVED, CANCELLED).contains(next);
            case RECEIVED -> next == INSPECTED;
            case INSPECTED -> next == COMPLETED;
            case COMPLETED, REJECTED, CANCELLED -> false;
        };
    }
}
