package com.locnguyen.ecommerce.domains.catalogasset.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.catalogasset.dto.*;
import com.locnguyen.ecommerce.domains.catalogasset.entity.CatalogAssetAssociation;
import com.locnguyen.ecommerce.domains.catalogasset.entity.CatalogUpload;
import com.locnguyen.ecommerce.domains.catalogasset.enums.CatalogAssetStatus;
import com.locnguyen.ecommerce.domains.catalogasset.enums.CatalogUploadPurpose;
import com.locnguyen.ecommerce.domains.catalogasset.repository.CatalogUploadRepository;
import com.locnguyen.ecommerce.domains.catalogasset.repository.CatalogAssetAssociationRepository;
import com.locnguyen.ecommerce.domains.catalogasset.service.CatalogAsset;
import com.locnguyen.ecommerce.domains.catalogasset.service.CatalogAssetService;
import com.locnguyen.ecommerce.infrastructure.storage.R2StorageProperties;
import com.locnguyen.ecommerce.infrastructure.storage.StorageObjectMetadata;
import com.locnguyen.ecommerce.infrastructure.storage.StorageService;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
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
    private final CatalogAssetAssociationRepository associationRepository;
    private final StorageService storageService;
    private final R2StorageProperties properties;

    @Override
    @Transactional
    public PresignedCatalogUploadResponse requestUpload(CreateCatalogUploadRequest request) {
        validateRequest(request);
        assertPurposeRole(request.getPurpose());
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
        return PresignedCatalogUploadResponse.builder().assetId(upload.getId()).storageKey(storageKey).uploadUrl(signed.url())
                .expiresAt(signed.expiresAt()).requiredContentType(contentType)
                .status(upload.getStatus().name()).associationStatus("UNATTACHED").build();
    }

    @Override
    @Transactional
    public CatalogAssetResponse completeUpload(String storageKey) {
        CatalogUpload upload = findUpload(storageKey);
        assertPurposeRole(upload.getPurpose());
        assertOwner(upload);
        if (upload.getStatus() == CatalogAssetStatus.ATTACHED || upload.getStatus() == CatalogAssetStatus.VERIFIED) return response(upload);
        if (upload.getStatus() != CatalogAssetStatus.REQUESTED || upload.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new AppException(ErrorCode.STORAGE_ASSET_NOT_VERIFIED, "Upload request has expired or cannot be completed");
        }
        StorageObjectMetadata object = storageService.headObject(upload.getStorageKey());
        if (!upload.getContentType().equalsIgnoreCase(object.contentType()) || upload.getSizeBytes() != object.sizeBytes()) {
            throw new AppException(ErrorCode.STORAGE_METADATA_MISMATCH);
        }
        if (!matchesImageSignature(upload.getContentType(), storageService.readObjectPrefix(upload.getStorageKey(), 12))) {
            throw new AppException(ErrorCode.STORAGE_METADATA_MISMATCH, "Uploaded bytes do not match the declared image type");
        }
        upload.setStatus(CatalogAssetStatus.VERIFIED);
        upload.setVerifiedAt(LocalDateTime.now(ZoneOffset.UTC));
        return response(upload);
    }

    @Override
    @Transactional
    public CatalogAsset claimVerifiedAsset(String storageKey) {
        return claimVerifiedAsset(storageKey, CatalogUploadPurpose.CATALOG_IMAGE);
    }

    @Override
    @Transactional
    public CatalogAsset claimVerifiedAsset(String storageKey, CatalogUploadPurpose purpose) {
        CatalogUpload upload = findUpload(storageKey);
        if (upload.getPurpose() != purpose) throw new AppException(ErrorCode.STORAGE_ASSET_NOT_VERIFIED, "Asset purpose does not match this field");
        if (upload.getStatus() != CatalogAssetStatus.VERIFIED) throw new AppException(ErrorCode.STORAGE_ASSET_NOT_VERIFIED);
        assertOwner(upload);
        upload.setStatus(CatalogAssetStatus.ATTACHED);
        return asset(upload);
    }

    @Override
    @Transactional
    public void associateAsset(String storageKey, CatalogUploadPurpose purpose, String entityType, UUID entityId) {
        CatalogUpload upload = findUpload(storageKey);
        if (upload.getPurpose() != purpose || upload.getStatus() != CatalogAssetStatus.ATTACHED) {
            throw new AppException(ErrorCode.STORAGE_ASSET_NOT_VERIFIED);
        }
        assertOwner(upload);
        var byAsset = associationRepository.findById(upload.getId());
        if (byAsset.isPresent()) {
            var existing = byAsset.get();
            if (existing.getEntityType().equals(entityType) && existing.getEntityId().equals(entityId)) return;
            throw new AppException(ErrorCode.CONFLICT, "Asset is already attached to another entity");
        }
        var byEntity = associationRepository.findByEntityTypeAndEntityId(entityType, entityId);
        if (byEntity.isPresent()) {
            if (byEntity.get().getUploadId().equals(upload.getId())) return;
            throw new AppException(ErrorCode.CONFLICT, "Entity already has a managed asset attached");
        }
        CatalogAssetAssociation association = new CatalogAssetAssociation();
        association.setUploadId(upload.getId());
        association.setEntityType(entityType);
        association.setEntityId(entityId);
        association.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        associationRepository.save(association);
    }

    @Override
    @Transactional
    public void detachAsset(String storageKey, String entityType, UUID entityId) {
        if (storageKey == null || storageKey.isBlank()) return;
        CatalogUpload upload = uploadRepository.findByStorageKey(storageKey).orElse(null);
        if (upload == null) return;
        var association = associationRepository.findById(upload.getId());
        if (association.isEmpty()) return;
        if (!association.get().getEntityType().equals(entityType) || !association.get().getEntityId().equals(entityId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Asset association does not belong to this entity");
        }
        associationRepository.deleteByUploadId(upload.getId());
        upload.setStatus(CatalogAssetStatus.DELETE_PENDING);
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
        // Keep unassociated ATTACHED rows: legacy product media may still reference these objects.
        // New attachments are associated in the same transaction and detached assets become DELETE_PENDING.
    }

    private void deletePending(CatalogUpload upload) {
        if (associationRepository.existsByUploadId(upload.getId())) {
            log.warn("Catalog storage cleanup skipped because asset is still referenced: storageKey={}", upload.getStorageKey());
            return;
        }
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

    private boolean matchesImageSignature(String contentType, byte[] bytes) {
        if (bytes == null) return false;
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
            case "image/png" -> bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47 && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a;
            case "image/webp" -> bytes.length >= 12 && bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46 && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50;
            default -> false;
        };
    }

    private String extensionFor(String contentType) {
        return switch (contentType) { case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> throw new AppException(ErrorCode.VALIDATION_ERROR); };
    }

    private CatalogUpload findUpload(String storageKey) {
        return uploadRepository.findByStorageKey(storageKey).orElseThrow(() -> new AppException(ErrorCode.STORAGE_OBJECT_NOT_FOUND));
    }

    private void assertOwner(CatalogUpload upload) {
        if (!SecurityUtils.getCurrentUsernameOrSystem().equals(upload.getCreatedBy())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Only the uploader can manage this asset");
        }
    }

    private void assertPurposeRole(CatalogUploadPurpose purpose) {
        if (purpose == CatalogUploadPurpose.CATEGORY_IMAGE
                || purpose == CatalogUploadPurpose.BRAND_LOGO
                || purpose == CatalogUploadPurpose.CARRIER_LOGO) {
            if (!SecurityUtils.hasAnyRole("STAFF", "ADMIN", "SUPER_ADMIN")) {
                throw new AppException(ErrorCode.FORBIDDEN, "You are not allowed to upload this asset purpose");
            }
            return;
        }
        if (!SecurityUtils.hasAnyRole("ADMIN", "SUPER_ADMIN")) {
            throw new AppException(ErrorCode.FORBIDDEN, "You are not allowed to upload this asset purpose");
        }
    }

    private CatalogAssetResponse response(CatalogUpload upload) {
        String associationStatus = associationRepository.existsByUploadId(upload.getId()) ? "ATTACHED" : "UNATTACHED";
        return CatalogAssetResponse.builder().assetId(upload.getId()).storageKey(upload.getStorageKey()).mediaUrl(storageService.publicUrl(upload.getStorageKey()))
                .contentType(upload.getContentType()).sizeBytes(upload.getSizeBytes()).originalFilename(upload.getOriginalFilename())
                .provider(upload.getProvider()).verifiedAt(upload.getVerifiedAt()).status(upload.getStatus().name())
                .associationStatus(associationStatus).build();
    }

    private CatalogAsset asset(CatalogUpload upload) {
        return new CatalogAsset(upload.getId(), upload.getStorageKey(), storageService.publicUrl(upload.getStorageKey()), upload.getContentType(), upload.getSizeBytes(), upload.getOriginalFilename(), upload.getProvider());
    }
}
