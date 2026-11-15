package com.locnguyen.ecommerce.domains.catalogasset.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class CatalogAssetResponse {
    private final UUID assetId;
    private final String storageKey;
    private final String mediaUrl;
    private final String contentType;
    private final long sizeBytes;
    private final String originalFilename;
    private final String provider;
    private final LocalDateTime verifiedAt;
    private final String status;
    private final String associationStatus;
}
