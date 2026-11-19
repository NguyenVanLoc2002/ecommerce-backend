package com.locnguyen.ecommerce.domains.shipment.repository;

import com.locnguyen.ecommerce.domains.shipment.entity.ShipmentBookingAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShipmentBookingAttemptRepository extends JpaRepository<ShipmentBookingAttempt, UUID> {

    Optional<ShipmentBookingAttempt> findByShipmentId(UUID shipmentId);
}
