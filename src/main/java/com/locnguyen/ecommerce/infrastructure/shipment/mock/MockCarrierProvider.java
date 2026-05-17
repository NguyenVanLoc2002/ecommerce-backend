package com.locnguyen.ecommerce.infrastructure.shipment.mock;

import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.provider.CarrierProvider;
import com.locnguyen.ecommerce.domains.carrier.provider.ShipmentTrackingResult;
import com.locnguyen.ecommerce.domains.carrier.provider.ShippingOrderResult;
import com.locnguyen.ecommerce.domains.carrier.provider.ShippingRateRequest;
import com.locnguyen.ecommerce.domains.carrier.provider.ShippingRateResult;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(name = "app.carrier.mock.enabled", havingValue = "true")
public class MockCarrierProvider implements CarrierProvider {

    @Override
    public String getProviderType() {
        return CarrierProviderType.MOCK.name();
    }

    @Override
    public ShippingRateResult calculateRate(ShippingRateRequest request, CarrierConfig config) {
        return ShippingRateResult.builder()
                .fee(BigDecimal.valueOf(25_000))
                .currency("VND")
                .serviceName("Mock same-day")
                .build();
    }

    @Override
    public ShippingOrderResult createShipment(Shipment shipment, Order order, CarrierConfig config) {
        String base = shipment.getShipmentCode();
        String trackingNumber = shipment.getTrackingNumber();
        if (trackingNumber == null || trackingNumber.isBlank()) {
            trackingNumber = "MOCK-TRK-" + base;
        }

        return ShippingOrderResult.builder()
                .carrierShipmentId("MOCK-SHP-" + base)
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
                .trackingUrl("https://mock-carrier.local/track/" + shipment.getShipmentCode())
                .status(ShipmentStatus.IN_TRANSIT)
                .rawStatus("IN_TRANSIT")
                .description("Mock carrier reports the shipment is in transit.")
                .location("Mock Hub")
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
