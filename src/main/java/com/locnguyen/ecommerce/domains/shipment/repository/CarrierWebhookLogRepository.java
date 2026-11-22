package com.locnguyen.ecommerce.domains.shipment.repository;

import com.locnguyen.ecommerce.domains.shipment.entity.CarrierWebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CarrierWebhookLogRepository extends JpaRepository<CarrierWebhookLog, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select w from CarrierWebhookLog w where w.eventKey = :eventKey")
    Optional<CarrierWebhookLog> findByEventKeyWithLock(
            @org.springframework.data.repository.query.Param("eventKey") String eventKey);

    boolean existsByEventKey(String eventKey);

    Optional<CarrierWebhookLog> findByEventKey(String eventKey);
}
