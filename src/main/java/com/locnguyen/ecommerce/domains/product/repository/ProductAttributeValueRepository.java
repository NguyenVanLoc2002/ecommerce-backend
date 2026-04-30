package com.locnguyen.ecommerce.domains.product.repository;

import com.locnguyen.ecommerce.domains.product.entity.ProductAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue, UUID> {

    Optional<ProductAttributeValue> findByAttributeCodeAndValue(String attributeCode, String value);

    Optional<ProductAttributeValue> findByAttributeIdAndValue(UUID attributeId, String value);

    boolean existsByAttributeIdAndValue(UUID attributeId, String value);

    boolean existsByAttributeIdAndValueAndIdNot(UUID attributeId, String value, UUID id);

    List<ProductAttributeValue> findByAttributeId(UUID attributeId);

    List<ProductAttributeValue> findByIdIn(Collection<UUID> ids);

    /**
     * True iff at least one (non-deleted) variant references the given attribute value.
     */
    @Query("""
            SELECT CASE WHEN COUNT(v) > 0 THEN TRUE ELSE FALSE END
            FROM com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant v
            JOIN v.attributeValues av
            WHERE av.id = :valueId
            """)
    boolean isUsedByAnyVariant(@Param("valueId") UUID valueId);
}
