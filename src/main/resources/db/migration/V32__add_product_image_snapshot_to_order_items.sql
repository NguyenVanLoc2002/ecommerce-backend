-- =====================================================
-- V32__add_product_image_snapshot_to_order_items.sql
-- Snapshot product image URL for order line items.
-- =====================================================

ALTER TABLE order_items
    ADD COLUMN product_image_url VARCHAR(500) NULL AFTER sku;
