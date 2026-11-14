package com.locnguyen.ecommerce.domains.catalogasset.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class PresignedCatalogUploadResponse {
    private final String storageKey;
    private final String uploadUrl;
    private final Instant expiresAt;
    private final String requiredContentType;
}
