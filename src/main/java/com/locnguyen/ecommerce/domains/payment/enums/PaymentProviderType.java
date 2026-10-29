package com.locnguyen.ecommerce.domains.payment.enums;

import java.util.Locale;

public enum PaymentProviderType {
    MOMO,
    PAYPAL,
    VNPAY,
    ZALO_PAY,
    BANK_TRANSFER,
    MOCK,
    UNKNOWN;

    public static PaymentProviderType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        if ("ZALOPAY".equals(normalized)) {
            normalized = "ZALO_PAY";
        }

        try {
            return PaymentProviderType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }
}
