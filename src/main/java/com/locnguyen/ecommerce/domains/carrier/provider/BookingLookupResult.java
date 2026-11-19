package com.locnguyen.ecommerce.domains.carrier.provider;

public record BookingLookupResult(Status status, ShippingOrderResult booking) {
    public enum Status {
        FOUND,
        CONFIRMED_ABSENT,
        UNKNOWN
    }

    public static BookingLookupResult found(ShippingOrderResult booking) {
        return new BookingLookupResult(Status.FOUND, booking);
    }

    public static BookingLookupResult confirmedAbsent() {
        return new BookingLookupResult(Status.CONFIRMED_ABSENT, null);
    }

    public static BookingLookupResult unknown() {
        return new BookingLookupResult(Status.UNKNOWN, null);
    }
}
