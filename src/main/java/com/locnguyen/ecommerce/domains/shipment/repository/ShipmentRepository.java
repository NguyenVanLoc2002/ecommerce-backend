package com.locnguyen.ecommerce.domains.shipment.repository;

import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

import java.util.UUID;
public interface ShipmentRepository extends JpaRepository<Shipment, UUID>,
        JpaSpecificationExecutor<Shipment> {

    Optional<Shipment> findByOrderId(UUID orderId);

    Optional<Shipment> findByShipmentCode(String shipmentCode);

    Optional<Shipment> findByCarrierShipmentId(String carrierShipmentId);

    Optional<Shipment> findByTrackingNumber(String trackingNumber);

    boolean existsByOrderId(UUID orderId);
}
