package com.locnguyen.ecommerce.domains.shipment.repository;

import com.locnguyen.ecommerce.domains.shipment.entity.CarrierWebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CarrierWebhookLogRepository extends JpaRepository<CarrierWebhookLog, UUID> {

    boolean existsByEventKey(String eventKey);

    Optional<CarrierWebhookLog> findByEventKey(String eventKey);
}
