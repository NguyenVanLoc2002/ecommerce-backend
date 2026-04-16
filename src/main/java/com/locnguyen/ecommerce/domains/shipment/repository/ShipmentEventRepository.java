package com.locnguyen.ecommerce.domains.shipment.repository;

import com.locnguyen.ecommerce.domains.shipment.entity.ShipmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent, Long> {

    List<ShipmentEvent> findByShipmentIdOrderByEventTimeAsc(Long shipmentId);
}
