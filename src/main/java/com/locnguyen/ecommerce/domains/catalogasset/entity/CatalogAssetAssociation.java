package com.locnguyen.ecommerce.domains.catalogasset.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "catalog_asset_associations")
@Getter
@Setter
@NoArgsConstructor
public class CatalogAssetAssociation {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "upload_id", nullable = false, length = 36)
    private UUID uploadId;

    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "entity_id", nullable = false, length = 36)
    private UUID entityId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
