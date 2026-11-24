package com.locnguyen.ecommerce.domains.returns;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.locnguyen.ecommerce.domains.returns.enums.ReturnStatus;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReturnStatusTest {

    @Test
    void transitions_followPhysicalReturnLifecycle() {
        assertTransitions(ReturnStatus.PENDING_DELIVERY_VERIFICATION,
                ReturnStatus.REQUESTED, ReturnStatus.REJECTED, ReturnStatus.CANCELLED);
        assertTransitions(ReturnStatus.REQUESTED,
                ReturnStatus.APPROVED, ReturnStatus.REJECTED, ReturnStatus.CANCELLED);
        assertTransitions(ReturnStatus.APPROVED, ReturnStatus.RECEIVED, ReturnStatus.CANCELLED);
        assertTransitions(ReturnStatus.RECEIVED, ReturnStatus.INSPECTED);
        assertTransitions(ReturnStatus.INSPECTED, ReturnStatus.COMPLETED);
        assertTransitions(ReturnStatus.COMPLETED);
        assertTransitions(ReturnStatus.REJECTED);
        assertTransitions(ReturnStatus.CANCELLED);
    }

    private void assertTransitions(ReturnStatus current, ReturnStatus... allowed) {
        Set<ReturnStatus> actual = EnumSet.noneOf(ReturnStatus.class);
        for (ReturnStatus candidate : ReturnStatus.values()) {
            if (current.canTransitionTo(candidate)) {
                actual.add(candidate);
            }
        }
        assertEquals(Set.of(allowed), actual);
    }
}
