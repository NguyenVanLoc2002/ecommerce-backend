package com.locnguyen.ecommerce.infrastructure.storage;

import java.time.Duration;

public interface StorageService {

    PresignedUpload presignPut(String storageKey, String contentType, Duration expiresIn);

    StorageObjectMetadata headObject(String storageKey);

    void deleteObject(String storageKey);

    String publicUrl(String storageKey);
}
