package com.locnguyen.ecommerce.domains.catalogasset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.catalogasset.dto.CreateCatalogUploadRequest;
import com.locnguyen.ecommerce.domains.catalogasset.entity.CatalogUpload;
import com.locnguyen.ecommerce.domains.catalogasset.entity.CatalogAssetAssociation;
import com.locnguyen.ecommerce.domains.catalogasset.enums.CatalogAssetStatus;
import com.locnguyen.ecommerce.domains.catalogasset.enums.CatalogUploadPurpose;
import com.locnguyen.ecommerce.domains.catalogasset.repository.CatalogUploadRepository;
import com.locnguyen.ecommerce.domains.catalogasset.repository.CatalogAssetAssociationRepository;
import com.locnguyen.ecommerce.domains.catalogasset.service.impl.CatalogAssetServiceImpl;
import com.locnguyen.ecommerce.infrastructure.storage.PresignedUpload;
import com.locnguyen.ecommerce.infrastructure.storage.R2StorageProperties;
import com.locnguyen.ecommerce.infrastructure.storage.StorageObjectMetadata;
import com.locnguyen.ecommerce.infrastructure.storage.StorageService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class CatalogAssetServiceTest {
    @Mock CatalogUploadRepository uploadRepository;
    @Mock CatalogAssetAssociationRepository associationRepository;
    @Mock StorageService storageService;
    private CatalogAssetServiceImpl service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "system", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        R2StorageProperties properties = new R2StorageProperties();
        properties.setPublicBaseUrl("https://assets.example.com");
        properties.setCatalogPrefix("catalog/products");
        properties.setPresignExpirySeconds(300);
        properties.setMaxImageSizeBytes(10_000);
        service = new CatalogAssetServiceImpl(uploadRepository, associationRepository, storageService, properties);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestUpload_whenValidImage_generatesServerManagedKeyAndSignsExactContentType() {
        CreateCatalogUploadRequest request = request("shoe.png", "image/png", 123);
        when(storageService.presignPut(any(), eq("image/png"), any())).thenReturn(new PresignedUpload("https://signed.example", Instant.now().plusSeconds(300)));

        var response = service.requestUpload(request);

        assertThat(response.getStorageKey()).startsWith("catalog/products/").endsWith(".png");
        assertThat(response.getStorageKey()).doesNotContain("shoe.png");
        verify(storageService).presignPut(eq(response.getStorageKey()), eq("image/png"), any());
        ArgumentCaptor<CatalogUpload> captor = ArgumentCaptor.forClass(CatalogUpload.class);
        verify(uploadRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CatalogAssetStatus.REQUESTED);
    }

    @Test
    void requestUpload_whenMimeIsNotAllowed_rejectsBeforeSigning() {
        assertThatThrownBy(() -> service.requestUpload(request("catalog.pdf", "application/pdf", 123)))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verifyNoInteractions(storageService);
    }

    @Test
    void requestUpload_whenStaffRequestsCategoryImage_allowsPurpose() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "staff", "", List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
        CreateCatalogUploadRequest request = request("category.png", "image/png", 123);
        request.setPurpose(CatalogUploadPurpose.CATEGORY_IMAGE);
        when(storageService.presignPut(any(), eq("image/png"), any())).thenReturn(new PresignedUpload("https://signed.example", Instant.now().plusSeconds(300)));

        var response = service.requestUpload(request);

        assertThat(response.getStorageKey()).startsWith("catalog/products/");
        verify(storageService).presignPut(eq(response.getStorageKey()), eq("image/png"), any());
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestUpload_whenStaffRequestsProductImage_forbidsPurpose() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "staff", "", List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));

        assertThatThrownBy(() -> service.requestUpload(request("product.png", "image/png", 123)))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        verifyNoInteractions(storageService);
        SecurityContextHolder.clearContext();
    }

    @Test
    void completeUpload_whenHeadMetadataMatches_verifiesAsset() {
        CatalogUpload upload = upload(CatalogAssetStatus.REQUESTED);
        when(uploadRepository.findByStorageKey("catalog/products/key.png")).thenReturn(Optional.of(upload));
        when(storageService.headObject(upload.getStorageKey())).thenReturn(new StorageObjectMetadata("image/png", 123));
        when(storageService.readObjectPrefix(upload.getStorageKey(), 12)).thenReturn(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        when(storageService.publicUrl(upload.getStorageKey())).thenReturn("https://assets.example.com/catalog/products/key.png");

        var response = service.completeUpload(upload.getStorageKey());

        assertThat(response.getStorageKey()).isEqualTo(upload.getStorageKey());
        assertThat(upload.getStatus()).isEqualTo(CatalogAssetStatus.VERIFIED);
        assertThat(upload.getVerifiedAt()).isNotNull();
    }

    @Test
    void completeUpload_whenHeadMetadataDiffers_rejectsAsset() {
        CatalogUpload upload = upload(CatalogAssetStatus.REQUESTED);
        when(uploadRepository.findByStorageKey(upload.getStorageKey())).thenReturn(Optional.of(upload));
        when(storageService.headObject(upload.getStorageKey())).thenReturn(new StorageObjectMetadata("image/jpeg", 123));

        assertThatThrownBy(() -> service.completeUpload(upload.getStorageKey()))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.STORAGE_METADATA_MISMATCH);
    }

    @Test
    void completeUpload_whenImageSignatureDoesNotMatch_rejectsAsset() {
        CatalogUpload upload = upload(CatalogAssetStatus.REQUESTED);
        when(uploadRepository.findByStorageKey(upload.getStorageKey())).thenReturn(Optional.of(upload));
        when(storageService.headObject(upload.getStorageKey())).thenReturn(new StorageObjectMetadata("image/png", 123));
        when(storageService.readObjectPrefix(upload.getStorageKey(), 12)).thenReturn(new byte[] {1, 2, 3, 4});

        assertThatThrownBy(() -> service.completeUpload(upload.getStorageKey()))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.STORAGE_METADATA_MISMATCH);
        assertThat(upload.getStatus()).isEqualTo(CatalogAssetStatus.REQUESTED);
    }

    @Test
    void claimVerifiedAsset_whenPurposeDoesNotMatch_rejectsAttachment() {
        CatalogUpload upload = upload(CatalogAssetStatus.VERIFIED);
        when(uploadRepository.findByStorageKey(upload.getStorageKey())).thenReturn(Optional.of(upload));

        assertThatThrownBy(() -> service.claimVerifiedAsset(upload.getStorageKey(), CatalogUploadPurpose.BRAND_LOGO))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.STORAGE_ASSET_NOT_VERIFIED);
        assertThat(upload.getStatus()).isEqualTo(CatalogAssetStatus.VERIFIED);
    }

    @Test
    void completeUpload_whenActorDoesNotOwnIntent_forbidsVerification() {
        CatalogUpload upload = upload(CatalogAssetStatus.REQUESTED);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "another-admin", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(uploadRepository.findByStorageKey(upload.getStorageKey())).thenReturn(Optional.of(upload));

        assertThatThrownBy(() -> service.completeUpload(upload.getStorageKey()))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        verifyNoInteractions(storageService);
    }

    @Test
    void associateAsset_whenSameAssociationAlreadyExists_isIdempotent() {
        UUID uploadId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        CatalogUpload upload = upload(CatalogAssetStatus.ATTACHED);
        ReflectionTestUtils.setField(upload, "id", uploadId);
        CatalogAssetAssociation existing = new CatalogAssetAssociation();
        existing.setUploadId(uploadId);
        existing.setEntityType("CATEGORY");
        existing.setEntityId(entityId);
        when(uploadRepository.findByStorageKey(upload.getStorageKey())).thenReturn(Optional.of(upload));
        when(associationRepository.findById(uploadId)).thenReturn(Optional.of(existing));

        service.associateAsset(upload.getStorageKey(), CatalogUploadPurpose.CATALOG_IMAGE, "CATEGORY", entityId);

        verify(associationRepository, never()).save(any());
    }

    @Test
    void associateAsset_whenAssetIsAssociatedToAnotherEntity_conflicts() {
        UUID uploadId = UUID.randomUUID();
        CatalogUpload upload = upload(CatalogAssetStatus.ATTACHED);
        ReflectionTestUtils.setField(upload, "id", uploadId);
        CatalogAssetAssociation existing = new CatalogAssetAssociation();
        existing.setUploadId(uploadId);
        existing.setEntityType("CATEGORY");
        existing.setEntityId(UUID.randomUUID());
        when(uploadRepository.findByStorageKey(upload.getStorageKey())).thenReturn(Optional.of(upload));
        when(associationRepository.findById(uploadId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.associateAsset(upload.getStorageKey(), CatalogUploadPurpose.CATALOG_IMAGE, "CATEGORY", UUID.randomUUID()))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void claimVerifiedAsset_whenNotVerified_rejectsAttachment() {
        CatalogUpload upload = upload(CatalogAssetStatus.REQUESTED);
        when(uploadRepository.findByStorageKey(upload.getStorageKey())).thenReturn(Optional.of(upload));

        assertThatThrownBy(() -> service.claimVerifiedAsset(upload.getStorageKey()))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.STORAGE_ASSET_NOT_VERIFIED);
    }

    @Test
    void deleteExpiredOrPendingAssets_keepsLegacyAttachedAssetsWithoutAssociation() {
        CatalogUpload legacyAsset = upload(CatalogAssetStatus.ATTACHED);
        UUID uploadId = UUID.randomUUID();
        ReflectionTestUtils.setField(legacyAsset, "id", uploadId);
        when(uploadRepository.findTop100ByStatusOrderByCreatedAtAsc(CatalogAssetStatus.DELETE_PENDING)).thenReturn(List.of());
        when(uploadRepository.findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(any(), any())).thenReturn(List.of());

        service.deleteExpiredOrPendingAssets();

        assertThat(legacyAsset.getStatus()).isEqualTo(CatalogAssetStatus.ATTACHED);
        verify(uploadRepository, never()).findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(eq(CatalogAssetStatus.ATTACHED), any());
        verifyNoInteractions(storageService);
    }
    private CreateCatalogUploadRequest request(String filename, String type, long size) {
        CreateCatalogUploadRequest request = new CreateCatalogUploadRequest();
        request.setPurpose(CatalogUploadPurpose.CATALOG_IMAGE);
        request.setOriginalFilename(filename);
        request.setContentType(type);
        request.setSizeBytes(size);
        return request;
    }

    private CatalogUpload upload(CatalogAssetStatus status) {
        CatalogUpload upload = new CatalogUpload();
        upload.setStorageKey("catalog/products/key.png");
        upload.setContentType("image/png");
        upload.setSizeBytes(123);
        upload.setOriginalFilename("key.png");
        upload.setProvider("R2");
        upload.setPurpose(CatalogUploadPurpose.CATALOG_IMAGE);
        ReflectionTestUtils.setField(upload, "createdBy", "system");
        upload.setStatus(status);
        upload.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1));
        return upload;
    }
}
