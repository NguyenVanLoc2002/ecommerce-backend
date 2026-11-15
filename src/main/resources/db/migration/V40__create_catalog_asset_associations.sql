CREATE TABLE catalog_asset_associations (
    upload_id   CHAR(36) PRIMARY KEY,
    entity_type VARCHAR(30) NOT NULL,
    entity_id   CHAR(36) NOT NULL,
    created_at  DATETIME NOT NULL,
    CONSTRAINT fk_catalog_asset_association_upload FOREIGN KEY (upload_id) REFERENCES catalog_uploads (id),
    CONSTRAINT uk_catalog_asset_association_entity UNIQUE (entity_type, entity_id),
    INDEX idx_catalog_asset_association_entity (entity_type, entity_id)
);

INSERT INTO catalog_asset_associations (upload_id, entity_type, entity_id, created_at)
SELECT upload.id, 'PRODUCT_MEDIA', media.id, UTC_TIMESTAMP()
FROM catalog_uploads upload
JOIN product_media media ON media.storage_key = upload.storage_key
WHERE upload.status = 'ATTACHED';
