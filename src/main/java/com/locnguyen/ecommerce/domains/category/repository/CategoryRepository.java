package com.locnguyen.ecommerce.domains.category.repository;

import com.locnguyen.ecommerce.domains.category.entity.Category;
import com.locnguyen.ecommerce.domains.category.enums.CategoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import java.util.UUID;
@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID>,
        JpaSpecificationExecutor<Category> {

    boolean existsBySlug(String slug);

    Optional<Category> findBySlug(String slug);

    Optional<Category> findByIdAndDeletedFalse(UUID id);

    List<Category> findByStatusOrderBySortOrderAsc(CategoryStatus status);

    List<Category> findByParentIdOrderBySortOrderAsc(UUID parentId);

    List<Category> findByIdInAndDeletedFalse(List<UUID> ids);
}
