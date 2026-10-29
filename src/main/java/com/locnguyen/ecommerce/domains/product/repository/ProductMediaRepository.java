package com.locnguyen.ecommerce.domains.product.repository;

import com.locnguyen.ecommerce.domains.product.entity.ProductMedia;
import com.locnguyen.ecommerce.domains.product.enums.MediaType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import java.util.UUID;
@Repository
public interface ProductMediaRepository extends JpaRepository<ProductMedia, UUID> {

    List<ProductMedia> findByProductIdOrderBySortOrderAsc(UUID productId);

    List<ProductMedia> findByProductIdAndVariantIdOrderBySortOrderAsc(UUID productId, UUID variantId);

    List<ProductMedia> findByProductIdAndVariantIdAndMediaTypeOrderBySortOrderAsc(
            UUID productId, UUID variantId, MediaType mediaType);

    List<ProductMedia> findByProductIdAndVariantIsNullAndMediaTypeOrderBySortOrderAsc(
            UUID productId, MediaType mediaType);
}
