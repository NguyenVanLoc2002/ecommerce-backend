package com.locnguyen.ecommerce.domains.brand.repository;

import com.locnguyen.ecommerce.domains.brand.entity.Brand;
import com.locnguyen.ecommerce.domains.brand.enums.BrandStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Long> {

    boolean existsBySlug(String slug);

    Optional<Brand> findBySlug(String slug);

    List<Brand> findByStatusOrderBySortOrderAsc(BrandStatus status);
}
