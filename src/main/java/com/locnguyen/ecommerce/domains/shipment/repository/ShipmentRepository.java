package com.locnguyen.ecommerce.domains.shipment.repository;

import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long>,
        JpaSpecificationExecutor<Shipment> {

    Optional<Shipment> findByOrderId(Long orderId);

    Optional<Shipment> findByShipmentCode(String shipmentCode);

    boolean existsByOrderId(Long orderId);
}
