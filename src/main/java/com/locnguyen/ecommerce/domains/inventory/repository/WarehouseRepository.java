package com.locnguyen.ecommerce.domains.inventory.repository;

import com.locnguyen.ecommerce.domains.inventory.entity.Warehouse;
import com.locnguyen.ecommerce.domains.inventory.enums.WarehouseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

import java.util.UUID;
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID>,
        JpaSpecificationExecutor<Warehouse> {

    boolean existsByCode(String code);

    Optional<Warehouse> findByCode(String code);

    Optional<Warehouse> findByIdAndDeletedFalse(UUID id);

    boolean existsByIdAndDeletedFalse(UUID id);

    List<Warehouse> findByStatusOrderByCreatedAtAsc(WarehouseStatus status);

    List<Warehouse> findByStatusAndDeletedFalseOrderByCreatedAtAsc(WarehouseStatus status);
}
