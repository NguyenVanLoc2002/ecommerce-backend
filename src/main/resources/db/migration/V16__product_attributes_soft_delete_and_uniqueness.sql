-- =====================================================
-- V16__product_attributes_soft_delete_and_uniqueness.sql
-- Bring product_attributes and product_attribute_values
-- under the project soft-delete convention so admin
-- attribute management never breaks variants in flight.
-- Adds (attribute_id, value) uniqueness so a single
-- attribute cannot store duplicate values.
-- =====================================================

ALTER TABLE product_attributes
    ADD COLUMN is_deleted BOOLEAN  NOT NULL DEFAULT FALSE,
    ADD COLUMN deleted_at DATETIME NULL,
    ADD COLUMN deleted_by VARCHAR(100) NULL;

CREATE INDEX idx_product_attributes_type ON product_attributes(type);

ALTER TABLE product_attribute_values
    ADD COLUMN is_deleted BOOLEAN  NOT NULL DEFAULT FALSE,
    ADD COLUMN deleted_at DATETIME NULL,
    ADD COLUMN deleted_by VARCHAR(100) NULL;

ALTER TABLE product_attribute_values
    ADD CONSTRAINT uq_pav_attribute_value UNIQUE (attribute_id, value);
