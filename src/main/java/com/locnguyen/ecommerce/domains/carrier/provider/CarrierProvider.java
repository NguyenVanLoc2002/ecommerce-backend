package com.locnguyen.ecommerce.domains.carrier.provider;

import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;


public interface CarrierProvider {

    String getProviderType();

    ShippingRateResult calculateRate(ShippingRateRequest request, CarrierConfig config);

    ShippingOrderResult createShipment(Shipment shipment, Order order, CarrierConfig config);

    default ShippingOrderResult createShipment(Shipment shipment, Order order, CarrierConfig config,
                                               String idempotencyKey) {
        return createShipment(shipment, order, config);
    }

    default CarrierProviderCapabilities capabilities() {
        return CarrierProviderCapabilities.unsupported();
    }

    /** Providers must explicitly opt in before a booking can ever be retried automatically. */
    default boolean supportsIdempotentBooking() {
        return capabilities().idempotentBooking();
    }

    /** Returns a confirmed booking only when the provider can look it up by our stable key. */
    default BookingLookupResult findShipmentByIdempotencyKey(
            String idempotencyKey, String shipmentCode, CarrierConfig config) {
        return BookingLookupResult.unknown();
    }

    default boolean supportsBookingLookup() {
        return capabilities().bookingLookup();
    }

    default boolean supportsCancellation() { return capabilities().cancellation(); }

    default boolean supportsTracking() { return capabilities().tracking(); }

    boolean cancelShipment(Shipment shipment, String reason, CarrierConfig config);

    ShipmentTrackingResult getTracking(Shipment shipment, CarrierConfig config);

    ShipmentStatus mapStatus(String carrierStatus);
}
