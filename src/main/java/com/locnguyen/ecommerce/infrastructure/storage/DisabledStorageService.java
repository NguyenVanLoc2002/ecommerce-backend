package com.locnguyen.ecommerce.infrastructure.storage;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.storage.r2", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledStorageService implements StorageService {

    private AppException disabled() {
        return new AppException(ErrorCode.STORAGE_CONFIGURATION_INVALID, "Catalog storage is not enabled");
    }

    @Override
    public PresignedUpload presignPut(String storageKey, String contentType, Duration expiresIn) {
        throw disabled();
    }

    @Override
    public StorageObjectMetadata headObject(String storageKey) {
        throw disabled();
    }

    @Override
    public void deleteObject(String storageKey) {
        throw disabled();
    }

    @Override
    public String publicUrl(String storageKey) {
        throw disabled();
    }
}
