package com.locnguyen.ecommerce.domains.catalogasset.service;

import com.locnguyen.ecommerce.domains.catalogasset.dto.*;

public interface CatalogAssetService {
    PresignedCatalogUploadResponse requestUpload(CreateCatalogUploadRequest request);
    CatalogAssetResponse completeUpload(String storageKey);
    CatalogAsset claimVerifiedAsset(String storageKey);
    CatalogAsset claimVerifiedAsset(String storageKey, com.locnguyen.ecommerce.domains.catalogasset.enums.CatalogUploadPurpose purpose);
    void associateAsset(String storageKey, com.locnguyen.ecommerce.domains.catalogasset.enums.CatalogUploadPurpose purpose, String entityType, java.util.UUID entityId);
    void detachAsset(String storageKey, String entityType, java.util.UUID entityId);
    void deleteExpiredOrPendingAssets();
}
