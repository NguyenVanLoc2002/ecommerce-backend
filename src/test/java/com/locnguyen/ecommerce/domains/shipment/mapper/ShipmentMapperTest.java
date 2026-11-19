package com.locnguyen.ecommerce.domains.shipment.mapper;

import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.enums.BookingAttemptStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentMapperTest {

    private final ShipmentMapper mapper = new ShipmentMapperImpl();

    @Test
    void toCustomerResponse_omitsInternalBookingAndCarrierCostDetails() {
        Shipment shipment = new Shipment();
        shipment.setOrder(new Order());
        shipment.setCarrierCost(new BigDecimal("12000.00"));
        shipment.setEstimatedCarrierCost(new BigDecimal("10000.00"));
        shipment.setBookingStatus(BookingAttemptStatus.UNKNOWN);
        shipment.setBookingAttemptKey("internal-attempt-key");
        shipment.setOverrideReasonCode("COST_CHANGE");
        shipment.setOverrideNote("internal note");

        var response = mapper.toCustomerResponse(shipment);

        assertThat(response.getCarrierCost()).isNull();
        assertThat(response.getEstimatedCarrierCost()).isNull();
        assertThat(response.getBookingStatus()).isNull();
        assertThat(response.getBookingAttemptKey()).isNull();
        assertThat(response.getOverrideReasonCode()).isNull();
        assertThat(response.getOverrideNote()).isNull();
    }
}
