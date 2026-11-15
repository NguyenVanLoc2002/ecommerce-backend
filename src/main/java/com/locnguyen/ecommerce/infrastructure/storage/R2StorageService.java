package com.locnguyen.ecommerce.infrastructure.storage;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.storage.r2", name = "enabled", havingValue = "true")
public class R2StorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final R2StorageProperties properties;

    @Override
    public PresignedUpload presignPut(String storageKey, String contentType, Duration expiresIn) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(storageKey)
                .contentType(contentType)
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(expiresIn)
                .putObjectRequest(request)
                .build());
        return new PresignedUpload(presigned.url().toExternalForm(), Instant.now().plus(expiresIn));
    }

    @Override
    public StorageObjectMetadata headObject(String storageKey) {
        try {
            var response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey)
                    .build());
            return new StorageObjectMetadata(response.contentType(), response.contentLength());
        } catch (NoSuchKeyException exception) {
            throw new AppException(ErrorCode.STORAGE_OBJECT_NOT_FOUND);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new AppException(ErrorCode.STORAGE_OBJECT_NOT_FOUND);
            }
            throw new AppException(ErrorCode.STORAGE_OPERATION_FAILED, "Unable to verify uploaded asset");
        }
    }

    @Override
    public byte[] readObjectPrefix(String storageKey, int maxBytes) {
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey)
                    .range("bytes=0-" + Math.max(0, maxBytes - 1))
                    .build()).asByteArray();
        } catch (NoSuchKeyException exception) {
            throw new AppException(ErrorCode.STORAGE_OBJECT_NOT_FOUND);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) throw new AppException(ErrorCode.STORAGE_OBJECT_NOT_FOUND);
            throw new AppException(ErrorCode.STORAGE_OPERATION_FAILED, "Unable to inspect uploaded asset");
        }
    }

    @Override
    public void deleteObject(String storageKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey)
                    .build());
        } catch (S3Exception exception) {
            throw new AppException(ErrorCode.STORAGE_OPERATION_FAILED, "Unable to delete catalog asset");
        }
    }

    @Override
    public String publicUrl(String storageKey) {
        String baseUrl = properties.getPublicBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new AppException(ErrorCode.STORAGE_CONFIGURATION_INVALID, "R2 public base URL is not configured");
        }
        return baseUrl.replaceAll("/+$", "") + "/" + storageKey;
    }
}
