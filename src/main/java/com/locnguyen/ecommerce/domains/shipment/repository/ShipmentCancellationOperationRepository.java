package com.locnguyen.ecommerce.domains.shipment.repository;

import com.locnguyen.ecommerce.domains.shipment.entity.ShipmentCancellationOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShipmentCancellationOperationRepository
        extends JpaRepository<ShipmentCancellationOperation, UUID> {

    Optional<ShipmentCancellationOperation> findByOrderId(UUID orderId);
}
