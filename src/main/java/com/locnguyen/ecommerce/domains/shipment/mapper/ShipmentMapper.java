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
                .carrierCost(shipment.getCarrierCost())
                .estimatedCarrierCost(shipment.getEstimatedCarrierCost())
                .bookingStatus(shipment.getBookingStatus())
                .bookingAttemptKey(shipment.getBookingAttemptKey())
                .overrideReasonCode(shipment.getOverrideReasonCode())
                .overrideNote(shipment.getOverrideNote())
                .overrideFromCarrierId(shipment.getOverrideFromCarrierId())
                .overrideToCarrierId(shipment.getOverrideToCarrierId())
                .overrideActor(shipment.getOverrideActor())
                .overrideAt(shipment.getOverrideAt())
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
                .carrierCost(shipment.getCarrierCost())
                .estimatedCarrierCost(shipment.getEstimatedCarrierCost())
                .bookingStatus(shipment.getBookingStatus())
                .bookingAttemptKey(shipment.getBookingAttemptKey())
                .createdAt(shipment.getCreatedAt())
                .build();
    }

    default ShipmentResponse toCustomerResponse(Shipment shipment) {
        ShipmentResponse response = toResponse(shipment);
        return ShipmentResponse.builder()
                .id(response.getId())
                .orderId(response.getOrderId())
                .orderCode(response.getOrderCode())
                .shipmentCode(response.getShipmentCode())
                .carrierId(response.getCarrierId())
                .carrierCode(response.getCarrierCode())
                .carrierProviderType(response.getCarrierProviderType())
                .carrier(response.getCarrier())
                .carrierShipmentId(response.getCarrierShipmentId())
                .trackingNumber(response.getTrackingNumber())
                .providerStatus(response.getProviderStatus())
                .providerTrackingUrl(response.getProviderTrackingUrl())
                .status(response.getStatus())
                .estimatedDeliveryDate(response.getEstimatedDeliveryDate())
                .deliveredAt(response.getDeliveredAt())
                .shippingFee(response.getShippingFee())
                .note(response.getNote())
                .events(response.getEvents())
                .createdAt(response.getCreatedAt())
                .updatedAt(response.getUpdatedAt())
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
