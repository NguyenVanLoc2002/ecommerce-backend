package com.locnguyen.ecommerce.domains.order.service;

import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import java.time.Duration;

/** Checkout holds remain temporary until payment or COD confirmation. */
public final class CheckoutHoldPolicy {
    public static final Duration ONLINE_WAIT = Duration.ofMinutes(30);
    public static final Duration COD_CONFIRMATION_WAIT = Duration.ofHours(24);

    private CheckoutHoldPolicy() {}

    public static Duration waitFor(PaymentMethod method) {
        return method == PaymentMethod.ONLINE ? ONLINE_WAIT : COD_CONFIRMATION_WAIT;
    }
}
