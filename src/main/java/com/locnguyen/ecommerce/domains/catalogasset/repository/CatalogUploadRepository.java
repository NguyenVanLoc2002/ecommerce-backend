package com.locnguyen.ecommerce.domains.catalogasset.repository;

import com.locnguyen.ecommerce.domains.catalogasset.entity.CatalogUpload;
import com.locnguyen.ecommerce.domains.catalogasset.enums.CatalogAssetStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogUploadRepository extends JpaRepository<CatalogUpload, UUID> {
    Optional<CatalogUpload> findByStorageKey(String storageKey);
    List<CatalogUpload> findTop100ByStatusOrderByCreatedAtAsc(CatalogAssetStatus status);
    List<CatalogUpload> findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(Collection<CatalogAssetStatus> statuses, LocalDateTime createdAt);
    List<CatalogUpload> findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(CatalogAssetStatus status, LocalDateTime createdAt);
}
