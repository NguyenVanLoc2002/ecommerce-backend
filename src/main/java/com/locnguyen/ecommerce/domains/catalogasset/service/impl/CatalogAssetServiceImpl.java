package com.locnguyen.ecommerce.domains.catalogasset.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.catalogasset.dto.*;
import com.locnguyen.ecommerce.domains.catalogasset.entity.CatalogUpload;
import com.locnguyen.ecommerce.domains.catalogasset.enums.CatalogAssetStatus;
import com.locnguyen.ecommerce.domains.catalogasset.repository.CatalogUploadRepository;
import com.locnguyen.ecommerce.domains.catalogasset.service.CatalogAsset;
import com.locnguyen.ecommerce.domains.catalogasset.service.CatalogAssetService;
import com.locnguyen.ecommerce.infrastructure.storage.R2StorageProperties;
import com.locnguyen.ecommerce.infrastructure.storage.StorageObjectMetadata;
import com.locnguyen.ecommerce.infrastructure.storage.StorageService;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogAssetServiceImpl implements CatalogAssetService {
    private static final String PROVIDER = "R2";
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final CatalogUploadRepository uploadRepository;
    private final StorageService storageService;
    private final R2StorageProperties properties;

    @Override
    @Transactional
    public PresignedCatalogUploadResponse requestUpload(CreateCatalogUploadRequest request) {
        validateRequest(request);
        String contentType = request.getContentType().trim().toLowerCase(Locale.ROOT);
        String storageKey = generateStorageKey(contentType);
        Duration expiry = Duration.ofSeconds(properties.getPresignExpirySeconds());
        if (expiry.isNegative() || expiry.isZero() || expiry.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new AppException(ErrorCode.STORAGE_CONFIGURATION_INVALID,
                    "Presigned upload expiry must be between 1 second and 15 minutes");
        }
        CatalogUpload upload = new CatalogUpload();
        upload.setStorageKey(storageKey);
        upload.setContentType(contentType);
        upload.setSizeBytes(request.getSizeBytes());
        upload.setOriginalFilename(safeFilename(request.getOriginalFilename()));
        upload.setProvider(PROVIDER);
        upload.setPurpose(request.getPurpose());
        upload.setStatus(CatalogAssetStatus.REQUESTED);
        upload.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plus(expiry));
        uploadRepository.save(upload);
        var signed = storageService.presignPut(storageKey, contentType, expiry);
        return PresignedCatalogUploadResponse.builder().storageKey(storageKey).uploadUrl(signed.url())
                .expiresAt(signed.expiresAt()).requiredContentType(contentType).build();
    }

    @Override
    @Transactional
    public CatalogAssetResponse completeUpload(String storageKey) {
        CatalogUpload upload = findUpload(storageKey);
        if (upload.getStatus() == CatalogAssetStatus.ATTACHED || upload.getStatus() == CatalogAssetStatus.VERIFIED) return response(upload);
        if (upload.getStatus() != CatalogAssetStatus.REQUESTED || upload.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new AppException(ErrorCode.STORAGE_ASSET_NOT_VERIFIED, "Upload request has expired or cannot be completed");
        }
        StorageObjectMetadata object = storageService.headObject(upload.getStorageKey());
        if (!upload.getContentType().equalsIgnoreCase(object.contentType()) || upload.getSizeBytes() != object.sizeBytes()) {
            throw new AppException(ErrorCode.STORAGE_METADATA_MISMATCH);
        }
        upload.setStatus(CatalogAssetStatus.VERIFIED);
        upload.setVerifiedAt(LocalDateTime.now(ZoneOffset.UTC));
        return response(upload);
    }

    @Override
    @Transactional
    public CatalogAsset claimVerifiedAsset(String storageKey) {
        CatalogUpload upload = findUpload(storageKey);
        if (upload.getStatus() != CatalogAssetStatus.VERIFIED) throw new AppException(ErrorCode.STORAGE_ASSET_NOT_VERIFIED);
        upload.setStatus(CatalogAssetStatus.ATTACHED);
        return asset(upload);
    }

    @Override
    @Transactional
    public void scheduleDelete(String storageKey) {
        if (storageKey != null && !storageKey.isBlank()) {
            uploadRepository.findByStorageKey(storageKey).ifPresent(upload -> upload.setStatus(CatalogAssetStatus.DELETE_PENDING));
        }
    }

    @Override
    @Transactional
    public void deleteExpiredOrPendingAssets() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusHours(properties.getOrphanRetentionHours());
        for (CatalogUpload upload : uploadRepository.findTop100ByStatusOrderByCreatedAtAsc(CatalogAssetStatus.DELETE_PENDING)) {
            deletePending(upload);
        }
        var statuses = EnumSet.of(CatalogAssetStatus.REQUESTED, CatalogAssetStatus.VERIFIED);
        for (CatalogUpload upload : uploadRepository.findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(statuses, cutoff)) {
            upload.setStatus(CatalogAssetStatus.DELETE_PENDING);
            deletePending(upload);
        }
    }

    private void deletePending(CatalogUpload upload) {
        try {
            storageService.deleteObject(upload.getStorageKey());
            upload.setStatus(CatalogAssetStatus.DELETED);
        } catch (AppException exception) {
            log.warn("Catalog storage cleanup will retry: storageKey={}", upload.getStorageKey());
        }
    }

    private void validateRequest(CreateCatalogUploadRequest request) {
        String contentType = request.getContentType() == null ? "" : request.getContentType().trim().toLowerCase(Locale.ROOT);
        if (request.getPurpose() == null || !IMAGE_TYPES.contains(contentType)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Only JPEG, PNG, and WebP catalog image uploads are allowed");
        }
        if (request.getSizeBytes() <= 0 || request.getSizeBytes() > properties.getMaxImageSizeBytes()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Catalog image size exceeds the allowed limit");
        }
        safeFilename(request.getOriginalFilename());
    }

    private String safeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) throw new AppException(ErrorCode.VALIDATION_ERROR, "Original filename is required");
        String filename = Paths.get(originalFilename).getFileName().toString().trim();
        if (filename.isBlank() || filename.length() > 255 || filename.contains("..") || filename.chars().anyMatch(Character::isISOControl)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Original filename is invalid");
        }
        return filename;
    }

    private String generateStorageKey(String contentType) {
        return properties.getCatalogPrefix().replaceAll("/+$", "") + "/" + UUID.randomUUID() + extensionFor(contentType);
    }

    private String extensionFor(String contentType) {
        return switch (contentType) { case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> throw new AppException(ErrorCode.VALIDATION_ERROR); };
    }

    private CatalogUpload findUpload(String storageKey) {
        return uploadRepository.findByStorageKey(storageKey).orElseThrow(() -> new AppException(ErrorCode.STORAGE_OBJECT_NOT_FOUND));
    }

    private CatalogAssetResponse response(CatalogUpload upload) {
        return CatalogAssetResponse.builder().storageKey(upload.getStorageKey()).mediaUrl(storageService.publicUrl(upload.getStorageKey()))
                .contentType(upload.getContentType()).sizeBytes(upload.getSizeBytes()).originalFilename(upload.getOriginalFilename())
                .provider(upload.getProvider()).verifiedAt(upload.getVerifiedAt()).build();
    }

    private CatalogAsset asset(CatalogUpload upload) {
        return new CatalogAsset(upload.getStorageKey(), storageService.publicUrl(upload.getStorageKey()), upload.getContentType(), upload.getSizeBytes(), upload.getOriginalFilename(), upload.getProvider());
    }
}
