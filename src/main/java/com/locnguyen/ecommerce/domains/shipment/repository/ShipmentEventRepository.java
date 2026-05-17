package com.locnguyen.ecommerce.domains.shipment.repository;

import com.locnguyen.ecommerce.domains.shipment.entity.ShipmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import java.time.LocalDateTime;
import java.util.UUID;
public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent, UUID> {

    List<ShipmentEvent> findByShipmentIdOrderByEventTimeAsc(UUID shipmentId);

    boolean existsByShipmentIdAndStatusAndDescriptionAndEventTime(
            UUID shipmentId,
            com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus status,
            String description,
            LocalDateTime eventTime);
}
