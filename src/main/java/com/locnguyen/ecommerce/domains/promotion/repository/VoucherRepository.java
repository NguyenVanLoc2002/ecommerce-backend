package com.locnguyen.ecommerce.domains.promotion.repository;

import com.locnguyen.ecommerce.domains.promotion.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long>,
        JpaSpecificationExecutor<Voucher> {

    Optional<Voucher> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
