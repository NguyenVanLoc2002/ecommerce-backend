package com.locnguyen.ecommerce.domains.carrier.provider;

import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;

public interface CarrierProvider {

    String getProviderType();

    ShippingRateResult calculateRate(ShippingRateRequest request, CarrierConfig config);

    ShippingOrderResult createShipment(Shipment shipment, Order order, CarrierConfig config);

    boolean cancelShipment(Shipment shipment, String reason, CarrierConfig config);

    ShipmentTrackingResult getTracking(Shipment shipment, CarrierConfig config);

    ShipmentStatus mapStatus(String carrierStatus);
}
