package com.locnguyen.ecommerce.domains.brand.repository;

import com.locnguyen.ecommerce.domains.brand.entity.Brand;
import com.locnguyen.ecommerce.domains.brand.enums.BrandStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import java.util.UUID;
@Repository
public interface BrandRepository extends JpaRepository<Brand, UUID>, JpaSpecificationExecutor<Brand> {

    boolean existsBySlug(String slug);

    Optional<Brand> findBySlug(String slug);

    Optional<Brand> findByIdAndDeletedFalse(UUID id);

    List<Brand> findByStatusOrderBySortOrderAsc(BrandStatus status);
}
