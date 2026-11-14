ALTER TABLE product_media
    ADD COLUMN storage_key VARCHAR(500) NULL,
    ADD COLUMN content_type VARCHAR(100) NULL,
    ADD COLUMN size_bytes BIGINT NULL,
    ADD COLUMN original_filename VARCHAR(255) NULL,
    ADD COLUMN provider VARCHAR(30) NULL,
    ADD CONSTRAINT uk_product_media_storage_key UNIQUE (storage_key);

CREATE TABLE catalog_uploads (
    id                CHAR(36) PRIMARY KEY,
    storage_key       VARCHAR(500) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    size_bytes        BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    provider          VARCHAR(30) NOT NULL,
    purpose           VARCHAR(30) NOT NULL,
    status            VARCHAR(30) NOT NULL,
    expires_at        DATETIME NOT NULL,
    verified_at       DATETIME NULL,
    created_at        DATETIME NOT NULL,
    created_by        VARCHAR(100) NULL,
    updated_at        DATETIME NOT NULL,
    updated_by        VARCHAR(100) NULL,
    CONSTRAINT uk_catalog_uploads_storage_key UNIQUE (storage_key),
    INDEX idx_catalog_uploads_cleanup (status, created_at)
);
