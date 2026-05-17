package com.locnguyen.ecommerce.domains.carrier.repository;

import com.locnguyen.ecommerce.domains.carrier.entity.Carrier;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CarrierRepository extends JpaRepository<Carrier, UUID>,
        JpaSpecificationExecutor<Carrier> {

    boolean existsByCodeIgnoreCase(String code);

    List<Carrier> findAllByStatus(CarrierStatus status);

    Optional<Carrier> findByCodeIgnoreCase(String code);

    Optional<Carrier> findFirstByProviderType(CarrierProviderType providerType);
}
