package com.locnguyen.ecommerce.domains.carrier.provider;

public record CarrierProviderCapabilities(
        boolean idempotentBooking,
        boolean bookingLookup,
        boolean cancellation,
        boolean tracking
) {
    public static CarrierProviderCapabilities unsupported() {
        return new CarrierProviderCapabilities(false, false, false, false);
    }
}
