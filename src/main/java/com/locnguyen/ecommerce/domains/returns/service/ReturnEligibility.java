package com.locnguyen.ecommerce.domains.returns.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnStatus;

import java.time.LocalDateTime;

public final class ReturnEligibility {
    private static final int RETURN_WINDOW_DAYS = 14;

    private ReturnEligibility() {}

    public static ReturnStatus initialStatus(OrderStatus orderStatus, LocalDateTime deliveredAt,
                                             LocalDateTime requestedAt) {
        if (orderStatus != OrderStatus.DELIVERED && orderStatus != OrderStatus.COMPLETED) {
            throw new AppException(ErrorCode.RETURN_NOT_ELIGIBLE, "The order must be delivered");
        }
        if (deliveredAt == null) {
            return ReturnStatus.PENDING_DELIVERY_VERIFICATION;
        }
        if (!canSubmit(orderStatus, deliveredAt, requestedAt)) {
            throw new AppException(ErrorCode.RETURN_NOT_ELIGIBLE, "The request is outside the delivery return window");
        }
        return ReturnStatus.REQUESTED;
    }

    public static boolean canSubmit(OrderStatus status, LocalDateTime deliveredAt, LocalDateTime now) {
        return (status == OrderStatus.DELIVERED || status == OrderStatus.COMPLETED)
                && (deliveredAt == null || (!now.isBefore(deliveredAt)
                        && !now.isAfter(deliveredAt.plusDays(RETURN_WINDOW_DAYS))));
    }

    public static void requireAvailableQuantity(int delivered, long claimed, int requested) {
        if (requested <= 0 || claimed < 0 || claimed > delivered || requested > delivered - claimed) {
            throw new AppException(ErrorCode.RETURN_QUANTITY_UNAVAILABLE);
        }
    }
}
