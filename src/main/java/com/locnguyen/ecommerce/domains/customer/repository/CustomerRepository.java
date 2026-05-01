package com.locnguyen.ecommerce.domains.customer.repository;

import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import java.util.UUID;
@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByUserId(UUID userId);

    Optional<Customer> findByUserIdAndDeletedFalse(UUID userId);

    java.util.List<Customer> findAllByDeletedFalse();

    java.util.List<Customer> findByIdInAndDeletedFalse(java.util.List<UUID> ids);
}
