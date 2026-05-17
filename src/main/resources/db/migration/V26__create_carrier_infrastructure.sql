-- =====================================================
-- V26__create_carrier_infrastructure.sql
-- Carrier catalog, encrypted store config, and shipment linkage
-- =====================================================

CREATE TABLE carriers (
    id             CHAR(36)      NOT NULL PRIMARY KEY,
    code           VARCHAR(50)   NOT NULL,
    name           VARCHAR(200)  NOT NULL,
    provider_type  VARCHAR(100)  NOT NULL,
    status         VARCHAR(50)   NOT NULL DEFAULT 'ACTIVE',
    logo_url       VARCHAR(500)  NULL,
    description    VARCHAR(500)  NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    created_by     VARCHAR(100)  NULL,
    updated_by     VARCHAR(100)  NULL,
    CONSTRAINT uq_carriers_code UNIQUE (code)
);

CREATE INDEX idx_carriers_provider_type ON carriers(provider_type);
CREATE INDEX idx_carriers_status ON carriers(status);

CREATE TABLE store_carrier_configs (
    id                  CHAR(36)      NOT NULL PRIMARY KEY,
    carrier_id          CHAR(36)      NOT NULL,
    api_key_enc         VARCHAR(1000) NULL,
    secret_key_enc      VARCHAR(1000) NULL,
    webhook_secret_enc  VARCHAR(1000) NULL,
    base_url            VARCHAR(500)  NULL,
    enabled             BOOLEAN       NOT NULL DEFAULT TRUE,
    config_json         TEXT          NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    created_by          VARCHAR(100)  NULL,
    updated_by          VARCHAR(100)  NULL,
    CONSTRAINT fk_scc_carrier FOREIGN KEY (carrier_id) REFERENCES carriers(id),
    CONSTRAINT uq_scc_carrier UNIQUE (carrier_id)
);

CREATE INDEX idx_scc_enabled ON store_carrier_configs(enabled);

ALTER TABLE shipments
    ADD COLUMN carrier_id CHAR(36) NULL,
    ADD COLUMN carrier_shipment_id VARCHAR(200) NULL;

ALTER TABLE shipments
    ADD CONSTRAINT fk_shipment_carrier FOREIGN KEY (carrier_id) REFERENCES carriers(id);

CREATE INDEX idx_shipments_carrier_id ON shipments(carrier_id);
CREATE INDEX idx_shipments_carrier_shipment_id ON shipments(carrier_shipment_id);
