package com.locnguyen.ecommerce.domains.returns;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnStatus;
import com.locnguyen.ecommerce.domains.returns.service.ReturnEligibility;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReturnEligibilityTest {
    private static final LocalDateTime DELIVERED_AT = LocalDateTime.of(2026, 11, 1, 10, 0);

    @Test
    void initialStatus_usesDeliveryTimeAndIncludesFourteenthDayBoundary() {
        LocalDateTime deadline = DELIVERED_AT.plusDays(14);
        assertEquals(ReturnStatus.REQUESTED,
                ReturnEligibility.initialStatus(OrderStatus.DELIVERED, DELIVERED_AT, deadline));
        AppException exception = assertThrows(AppException.class,
                () -> ReturnEligibility.initialStatus(OrderStatus.COMPLETED, DELIVERED_AT, deadline.plusNanos(1)));
        assertEquals(ErrorCode.RETURN_NOT_ELIGIBLE, exception.getErrorCode());
    }

    @Test
    void initialStatus_routesMissingDeliveryTimestampToVerification() {
        assertEquals(ReturnStatus.PENDING_DELIVERY_VERIFICATION,
                ReturnEligibility.initialStatus(OrderStatus.COMPLETED, null, DELIVERED_AT));
        assertThrows(AppException.class,
                () -> ReturnEligibility.initialStatus(OrderStatus.SHIPPED, null, DELIVERED_AT));
    }

    @Test
    void requireAvailableQuantity_subtractsPreviouslyClaimedUnits() {
        assertDoesNotThrow(() -> ReturnEligibility.requireAvailableQuantity(5, 3, 2));
        AppException exception = assertThrows(AppException.class,
                () -> ReturnEligibility.requireAvailableQuantity(5, 3, 3));
        assertEquals(ErrorCode.RETURN_QUANTITY_UNAVAILABLE, exception.getErrorCode());
    }
}
