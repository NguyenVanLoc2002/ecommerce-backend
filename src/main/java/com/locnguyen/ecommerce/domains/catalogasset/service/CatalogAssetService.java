package com.locnguyen.ecommerce.domains.catalogasset.service;

import com.locnguyen.ecommerce.domains.catalogasset.dto.*;

public interface CatalogAssetService {
    PresignedCatalogUploadResponse requestUpload(CreateCatalogUploadRequest request);
    CatalogAssetResponse completeUpload(String storageKey);
    CatalogAsset claimVerifiedAsset(String storageKey);
    void scheduleDelete(String storageKey);
    void deleteExpiredOrPendingAssets();
}
