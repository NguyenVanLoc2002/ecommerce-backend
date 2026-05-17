package com.locnguyen.ecommerce.infrastructure.shipment.mock;

import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockCarrierProviderTest {

    private final MockCarrierProvider provider = new MockCarrierProvider();

    @Test
    void createShipment_returnsMockTrackingNumberWhenMissing() {
        Shipment shipment = new Shipment();
        shipment.setShipmentCode("SHP001");
        Order order = new Order();

        var result = provider.createShipment(shipment, order, null);

        assertThat(result.carrierShipmentId()).isEqualTo("MOCK-SHP-SHP001");
        assertThat(result.trackingNumber()).isEqualTo("MOCK-TRK-SHP001");
        assertThat(result.rawStatus()).isEqualTo("PENDING");
    }

    @Test
    void createShipment_preservesProvidedTrackingNumber() {
        Shipment shipment = new Shipment();
        shipment.setShipmentCode("SHP002");
        shipment.setTrackingNumber("TRACK-002");

        var result = provider.createShipment(shipment, new Order(), null);

        assertThat(result.trackingNumber()).isEqualTo("TRACK-002");
    }

    @Test
    void mapStatus_unknownStatus_fallsBackToPending() {
        assertThat(provider.mapStatus("unknown")).isEqualTo(ShipmentStatus.PENDING);
        assertThat(provider.mapStatus(null)).isEqualTo(ShipmentStatus.PENDING);
    }
}
