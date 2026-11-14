package com.locnguyen.ecommerce.domains.catalogasset.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.catalogasset.enums.CatalogAssetStatus;
import com.locnguyen.ecommerce.domains.catalogasset.enums.CatalogUploadPurpose;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "catalog_uploads")
@Getter
@Setter
@NoArgsConstructor
public class CatalogUpload extends BaseEntity {
    @Column(name = "storage_key", nullable = false, unique = true, length = 500) private String storageKey;
    @Column(name = "content_type", nullable = false, length = 100) private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(name = "original_filename", nullable = false, length = 255) private String originalFilename;
    @Column(name = "provider", nullable = false, length = 30) private String provider;
    @Enumerated(EnumType.STRING) @Column(name = "purpose", nullable = false, length = 30) private CatalogUploadPurpose purpose;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 30) private CatalogAssetStatus status;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "verified_at") private LocalDateTime verifiedAt;
}
