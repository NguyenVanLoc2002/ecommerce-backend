package com.locnguyen.ecommerce.domains.carrier.provider;

import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class ManualCarrierProvider implements CarrierProvider {

    @Override
    public String getProviderType() {
        return CarrierProviderType.MANUAL.name();
    }

    @Override
    public ShippingRateResult calculateRate(ShippingRateRequest request, CarrierConfig config) {
        return ShippingRateResult.builder()
                .fee(BigDecimal.ZERO)
                .currency("VND")
                .serviceName("Manual fulfillment")
                .build();
    }

    @Override
    public ShippingOrderResult createShipment(Shipment shipment, Order order, CarrierConfig config) {
        String trackingNumber = shipment.getTrackingNumber();
        if (trackingNumber == null || trackingNumber.isBlank()) {
            trackingNumber = shipment.getShipmentCode();
        }
        return ShippingOrderResult.builder()
                .carrierShipmentId(shipment.getShipmentCode())
                .trackingNumber(trackingNumber)
                .rawStatus(ShipmentStatus.PENDING.name())
                .build();
    }

    @Override
    public boolean cancelShipment(Shipment shipment, String reason, CarrierConfig config) {
        return true;
    }

    @Override
    public ShipmentTrackingResult getTracking(Shipment shipment, CarrierConfig config) {
        return ShipmentTrackingResult.builder()
                .trackingNumber(shipment.getTrackingNumber())
                .status(ShipmentStatus.PENDING)
                .rawStatus(ShipmentStatus.PENDING.name())
                .description("Manual carrier tracking is maintained internally.")
                .eventTime(LocalDateTime.now())
                .build();
    }

    @Override
    public ShipmentStatus mapStatus(String carrierStatus) {
        if (carrierStatus == null || carrierStatus.isBlank()) {
            return ShipmentStatus.PENDING;
        }
        try {
            return ShipmentStatus.valueOf(carrierStatus.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ShipmentStatus.PENDING;
        }
    }
}
