package com.locnguyen.ecommerce.domains.catalogasset.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CatalogAssetCleanupJob {
    private final CatalogAssetService catalogAssetService;

    @Scheduled(fixedDelayString = "${app.storage.r2.cleanup-fixed-delay-ms:300000}")
    public void removeExpiredAndDeletedAssets() { catalogAssetService.deleteExpiredOrPendingAssets(); }
}
