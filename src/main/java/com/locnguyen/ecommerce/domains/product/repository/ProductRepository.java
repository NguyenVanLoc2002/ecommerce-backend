package com.locnguyen.ecommerce.domains.product.repository;

import com.locnguyen.ecommerce.domains.product.entity.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import java.util.UUID;
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>,
        JpaSpecificationExecutor<Product> {

    boolean existsBySlug(String slug);

    Optional<Product> findBySlug(String slug);

    Optional<Product> findByIdAndDeletedFalse(UUID id);

    boolean existsByIdAndDeletedFalse(UUID id);

    @EntityGraph(attributePaths = {"brand", "categories"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    @Query("""
            SELECT DISTINCT p FROM Product p
            LEFT JOIN FETCH p.brand
            LEFT JOIN FETCH p.categories
            LEFT JOIN FETCH p.variants v
            LEFT JOIN FETCH v.attributeValues av
            LEFT JOIN FETCH av.attribute
            LEFT JOIN FETCH p.media
            WHERE p.id = :id
              AND p.deleted = false
            """)
    Optional<Product> findDetailById(@Param("id") UUID id);
}
