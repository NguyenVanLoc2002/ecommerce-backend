package com.locnguyen.ecommerce.domains.shipment.mapper;

import com.locnguyen.ecommerce.domains.shipment.dto.ShipmentEventResponse;
import com.locnguyen.ecommerce.domains.shipment.dto.ShipmentResponse;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.entity.ShipmentEvent;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ShipmentMapper {

    /**
     * Full response including tracking event timeline.
     * Use for single-item GET endpoints.
     */
    default ShipmentResponse toResponse(Shipment shipment) {
        if (shipment == null) return null;

        return ShipmentResponse.builder()
                .id(shipment.getId())
                .orderId(shipment.getOrder().getId())
                .orderCode(shipment.getOrder().getOrderCode())
                .shipmentCode(shipment.getShipmentCode())
                .carrier(shipment.getCarrier())
                .trackingNumber(shipment.getTrackingNumber())
                .status(shipment.getStatus())
                .estimatedDeliveryDate(shipment.getEstimatedDeliveryDate())
                .deliveredAt(shipment.getDeliveredAt())
                .shippingFee(shipment.getShippingFee())
                .note(shipment.getNote())
                .events(toEventResponses(shipment.getEvents()))
                .createdAt(shipment.getCreatedAt())
                .updatedAt(shipment.getUpdatedAt())
                .build();
    }

    /**
     * Lightweight response without tracking events.
     * Use for paginated list views.
     */
    default ShipmentResponse toListItemResponse(Shipment shipment) {
        if (shipment == null) return null;

        return ShipmentResponse.builder()
                .id(shipment.getId())
                .orderId(shipment.getOrder().getId())
                .orderCode(shipment.getOrder().getOrderCode())
                .shipmentCode(shipment.getShipmentCode())
                .carrier(shipment.getCarrier())
                .trackingNumber(shipment.getTrackingNumber())
                .status(shipment.getStatus())
                .estimatedDeliveryDate(shipment.getEstimatedDeliveryDate())
                .deliveredAt(shipment.getDeliveredAt())
                .shippingFee(shipment.getShippingFee())
                .createdAt(shipment.getCreatedAt())
                .build();
    }

    default ShipmentEventResponse toEventResponse(ShipmentEvent event) {
        if (event == null) return null;

        return ShipmentEventResponse.builder()
                .id(event.getId())
                .status(event.getStatus())
                .location(event.getLocation())
                .description(event.getDescription())
                .eventTime(event.getEventTime())
                .build();
    }

    List<ShipmentEventResponse> toEventResponses(List<ShipmentEvent> events);
}
