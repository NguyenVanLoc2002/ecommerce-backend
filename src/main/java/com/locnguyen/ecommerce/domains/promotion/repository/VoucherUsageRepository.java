package com.locnguyen.ecommerce.domains.promotion.repository;

import com.locnguyen.ecommerce.domains.promotion.entity.VoucherUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, Long> {

    Page<VoucherUsage> findByVoucherId(Long voucherId, Pageable pageable);

    long countByVoucherId(Long voucherId);

    long countByVoucherIdAndCustomerId(Long voucherId, Long customerId);

    boolean existsByVoucherIdAndOrderId(Long voucherId, Long orderId);

    @Query("SELECT COUNT(vu) > 0 FROM VoucherUsage vu " +
           "WHERE vu.customer.id = :customerId " +
           "AND vu.voucher.promotion.id = :promotionId")
    boolean existsByCustomerIdAndPromotionId(
            @Param("customerId") Long customerId,
            @Param("promotionId") Long promotionId);
}
