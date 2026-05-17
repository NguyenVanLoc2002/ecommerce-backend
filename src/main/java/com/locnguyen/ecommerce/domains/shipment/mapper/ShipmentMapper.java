package com.locnguyen.ecommerce.domains.shipment.mapper;

import com.locnguyen.ecommerce.domains.shipment.dto.ShipmentEventResponse;
import com.locnguyen.ecommerce.domains.shipment.dto.ShipmentResponse;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.entity.ShipmentEvent;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ShipmentMapper {

    default ShipmentResponse toResponse(Shipment shipment) {
        if (shipment == null) {
            return null;
        }

        return ShipmentResponse.builder()
                .id(shipment.getId())
                .orderId(shipment.getOrder().getId())
                .orderCode(shipment.getOrder().getOrderCode())
                .shipmentCode(shipment.getShipmentCode())
                .carrierId(shipment.getCarrierEntity() != null ? shipment.getCarrierEntity().getId() : null)
                .carrierCode(shipment.getCarrierEntity() != null ? shipment.getCarrierEntity().getCode() : null)
                .carrierProviderType(shipment.getCarrierEntity() != null
                        ? shipment.getCarrierEntity().getProviderType() : null)
                .carrier(shipment.getCarrier())
                .carrierShipmentId(shipment.getCarrierShipmentId())
                .trackingNumber(shipment.getTrackingNumber())
                .providerStatus(shipment.getProviderStatus())
                .providerTrackingUrl(shipment.getProviderTrackingUrl())
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

    default ShipmentResponse toListItemResponse(Shipment shipment) {
        if (shipment == null) {
            return null;
        }

        return ShipmentResponse.builder()
                .id(shipment.getId())
                .orderId(shipment.getOrder().getId())
                .orderCode(shipment.getOrder().getOrderCode())
                .shipmentCode(shipment.getShipmentCode())
                .carrierId(shipment.getCarrierEntity() != null ? shipment.getCarrierEntity().getId() : null)
                .carrierCode(shipment.getCarrierEntity() != null ? shipment.getCarrierEntity().getCode() : null)
                .carrierProviderType(shipment.getCarrierEntity() != null
                        ? shipment.getCarrierEntity().getProviderType() : null)
                .carrier(shipment.getCarrier())
                .carrierShipmentId(shipment.getCarrierShipmentId())
                .trackingNumber(shipment.getTrackingNumber())
                .providerStatus(shipment.getProviderStatus())
                .providerTrackingUrl(shipment.getProviderTrackingUrl())
                .status(shipment.getStatus())
                .estimatedDeliveryDate(shipment.getEstimatedDeliveryDate())
                .deliveredAt(shipment.getDeliveredAt())
                .shippingFee(shipment.getShippingFee())
                .createdAt(shipment.getCreatedAt())
                .build();
    }

    default ShipmentEventResponse toEventResponse(ShipmentEvent event) {
        if (event == null) {
            return null;
        }

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
