package com.locnguyen.ecommerce.domains.shipment.repository;

import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

import java.util.UUID;
public interface ShipmentRepository extends JpaRepository<Shipment, UUID>,
        JpaSpecificationExecutor<Shipment> {

    Optional<Shipment> findByOrderId(UUID orderId);

    @Query("SELECT s.order.id FROM Shipment s WHERE s.id = :shipmentId")
    Optional<UUID> findOrderIdByShipmentId(@Param("shipmentId") UUID shipmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shipment s WHERE s.order.id = :orderId")
    Optional<Shipment> findByOrderIdWithLock(@Param("orderId") UUID orderId);

    Optional<Shipment> findByShipmentCode(String shipmentCode);

    Optional<Shipment> findByCarrierShipmentId(String carrierShipmentId);

    Optional<Shipment> findByTrackingNumber(String trackingNumber);

    boolean existsByOrderId(UUID orderId);

    List<Shipment> findByOrderIdIn(List<UUID> orderIds);
}
