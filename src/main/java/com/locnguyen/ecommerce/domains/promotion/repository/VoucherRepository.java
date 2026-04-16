package com.locnguyen.ecommerce.domains.promotion.repository;

import com.locnguyen.ecommerce.domains.promotion.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long>,
        JpaSpecificationExecutor<Voucher> {

    Optional<Voucher> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    /**
     * Loads the voucher with its promotion and promotion rules in one query.
     * Use this during validation and application to avoid N+1 on rules iteration.
     */
    @Query("""
            SELECT v FROM Voucher v
            JOIN FETCH v.promotion p
            LEFT JOIN FETCH p.rules
            WHERE UPPER(v.code) = UPPER(:code)
            AND v.deleted = false
            """)
    Optional<Voucher> findByCodeWithRules(@Param("code") String code);

    /**
     * Atomically increments the usage counter without a read-modify-write cycle.
     * Safe under concurrent redemptions because the UPDATE is issued as one DB statement.
     */
    @Modifying
    @Query("UPDATE Voucher v SET v.usageCount = v.usageCount + 1 WHERE v.id = :id")
    void incrementUsageCount(@Param("id") Long id);

    /**
     * Atomically decrements the usage counter, floored at zero.
     */
    @Modifying
    @Query("""
            UPDATE Voucher v
            SET v.usageCount = CASE WHEN v.usageCount > 0 THEN v.usageCount - 1 ELSE 0 END
            WHERE v.id = :id
            """)
    void decrementUsageCount(@Param("id") Long id);
}
