package com.locnguyen.ecommerce.domains.catalogasset.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class PresignedCatalogUploadResponse {
    private final UUID assetId;
    private final String storageKey;
    private final String uploadUrl;
    private final Instant expiresAt;
    private final String requiredContentType;
    private final String status;
    private final String associationStatus;
}
