ALTER TABLE categories
    ADD COLUMN asset_storage_key VARCHAR(500) NULL,
    ADD CONSTRAINT uk_categories_asset_storage_key UNIQUE (asset_storage_key),
    ADD CONSTRAINT fk_categories_asset_storage_key FOREIGN KEY (asset_storage_key) REFERENCES catalog_uploads (storage_key);

ALTER TABLE brands
    ADD COLUMN asset_storage_key VARCHAR(500) NULL,
    ADD CONSTRAINT uk_brands_asset_storage_key UNIQUE (asset_storage_key),
    ADD CONSTRAINT fk_brands_asset_storage_key FOREIGN KEY (asset_storage_key) REFERENCES catalog_uploads (storage_key);

ALTER TABLE customers
    ADD COLUMN asset_storage_key VARCHAR(500) NULL,
    ADD CONSTRAINT uk_customers_asset_storage_key UNIQUE (asset_storage_key),
    ADD CONSTRAINT fk_customers_asset_storage_key FOREIGN KEY (asset_storage_key) REFERENCES catalog_uploads (storage_key);

ALTER TABLE carriers
    ADD COLUMN asset_storage_key VARCHAR(500) NULL,
    ADD CONSTRAINT uk_carriers_asset_storage_key UNIQUE (asset_storage_key),
    ADD CONSTRAINT fk_carriers_asset_storage_key FOREIGN KEY (asset_storage_key) REFERENCES catalog_uploads (storage_key);
