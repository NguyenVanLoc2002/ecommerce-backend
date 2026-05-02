package com.locnguyen.ecommerce.domains.address.repository;

import com.locnguyen.ecommerce.domains.address.entity.Address;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

import java.util.UUID;
@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findByCustomerId(UUID customerId, Sort sort);

    List<Address> findByCustomerIdAndDeletedFalse(UUID customerId, Sort sort);

    java.util.Optional<Address> findByIdAndDeletedFalse(java.util.UUID id);

    @Modifying
    @Query("UPDATE Address a SET a.defaultAddress = false WHERE a.customer.id = :customerId AND a.defaultAddress = true")
    void clearDefaultByCustomerId(@Param("customerId") UUID customerId);
}
