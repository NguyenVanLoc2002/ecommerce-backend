package com.locnguyen.ecommerce.domains.catalogasset.service;

public record CatalogAsset(String storageKey, String mediaUrl, String contentType, long sizeBytes, String originalFilename, String provider) {}
