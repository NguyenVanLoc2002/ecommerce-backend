package com.locnguyen.ecommerce.domains.customer.repository;

import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository
        extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByUserId(UUID userId);

    Optional<Customer> findByUserIdAndDeletedFalse(UUID userId);

    Optional<Customer> findByIdAndDeletedFalse(UUID id);

    List<Customer> findAllByDeletedFalse();

    List<Customer> findByIdInAndDeletedFalse(List<UUID> ids);
}
