package com.locnguyen.ecommerce.domains.promotion.repository;

import com.locnguyen.ecommerce.domains.promotion.entity.VoucherUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.Optional;
public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, UUID> {

    Page<VoucherUsage> findByVoucherId(UUID voucherId, Pageable pageable);

    long countByVoucherId(UUID voucherId);

    long countByVoucherIdAndCustomerId(UUID voucherId, UUID customerId);

    boolean existsByVoucherIdAndOrderId(UUID voucherId, UUID orderId);

    Optional<VoucherUsage> findByVoucherIdAndOrderId(UUID voucherId, UUID orderId);

    @Query("SELECT COUNT(vu) > 0 FROM VoucherUsage vu " +
           "WHERE vu.customer.id = :customerId " +
           "AND vu.voucher.promotion.id = :promotionId")
    boolean existsByCustomerIdAndPromotionId(
            @Param("customerId") UUID customerId,
            @Param("promotionId") UUID promotionId);
}
