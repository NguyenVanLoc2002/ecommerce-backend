package com.locnguyen.ecommerce.domains.productvariant.repository;

import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    boolean existsBySku(String sku);

    boolean existsByBarcode(String barcode);

    boolean existsByIdAndDeletedFalse(UUID id);

    Optional<ProductVariant> findBySku(String sku);

    Optional<ProductVariant> findByIdAndDeletedFalse(UUID id);

    Optional<ProductVariant> findByIdAndProductId(UUID id, UUID productId);

    Optional<ProductVariant> findByIdAndProductIdAndDeletedFalse(UUID id, UUID productId);

    List<ProductVariant> findByProductIdOrderByCreatedAtAsc(UUID productId);

    List<ProductVariant> findByProductIdAndDeletedFalseOrderByCreatedAtAsc(UUID productId);

    long countByProductId(UUID productId);

    /**
     * Returns ids of variants under {@code productId} whose attribute-value set is
     * exactly {@code valueIds} (same size and contains all of them).
     *
     * <p>Used to detect duplicate variant combinations before insert/update.
     */
    @Query("""
            SELECT v.id
            FROM ProductVariant v
            JOIN v.attributeValues av
            WHERE v.product.id = :productId
              AND v.deleted = false
              AND av.id IN :valueIds
            GROUP BY v.id
            HAVING COUNT(DISTINCT av.id) = :size
                AND (SELECT COUNT(av2)
                     FROM ProductVariant v2
                     JOIN v2.attributeValues av2
                     WHERE v2.id = v.id
                       AND v2.deleted = false) = :size
            """)
    List<UUID> findVariantIdsWithExactAttributeSet(@Param("productId") UUID productId,
                                                   @Param("valueIds") Collection<UUID> valueIds,
                                                   @Param("size") long size);
}
