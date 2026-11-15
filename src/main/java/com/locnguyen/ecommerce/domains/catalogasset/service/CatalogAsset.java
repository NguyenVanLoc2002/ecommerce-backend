package com.locnguyen.ecommerce.domains.catalogasset.service;

public record CatalogAsset(java.util.UUID assetId, String storageKey, String mediaUrl, String contentType, long sizeBytes, String originalFilename, String provider) {}
