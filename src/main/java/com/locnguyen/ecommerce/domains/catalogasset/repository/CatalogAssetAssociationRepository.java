package com.locnguyen.ecommerce.domains.catalogasset.repository;

import com.locnguyen.ecommerce.domains.catalogasset.entity.CatalogAssetAssociation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogAssetAssociationRepository extends JpaRepository<CatalogAssetAssociation, UUID> {
    Optional<CatalogAssetAssociation> findByEntityTypeAndEntityId(String entityType, UUID entityId);
    boolean existsByUploadId(UUID uploadId);
    void deleteByUploadId(UUID uploadId);
}
