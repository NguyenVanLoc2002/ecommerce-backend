package com.locnguyen.ecommerce.domains.carrier.repository;

import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CarrierConfigRepository extends JpaRepository<CarrierConfig, UUID> {

    Optional<CarrierConfig> findByCarrierId(UUID carrierId);
}
