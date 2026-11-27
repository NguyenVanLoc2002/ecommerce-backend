CREATE TABLE shipping_policy_versions (
    version BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    revision INT NOT NULL DEFAULT 1,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    status VARCHAR(12) NOT NULL,
    effective_from DATETIME(6) NOT NULL,
    published_at DATETIME(6),
    published_by CHAR(36),
    archived_at DATETIME(6),
    CONSTRAINT chk_shipping_policy_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    INDEX idx_shipping_policy_effective (effective_from DESC, version DESC)
);

CREATE TABLE shipping_policy_history (
    version BIGINT NOT NULL,
    revision INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    status VARCHAR(12) NOT NULL,
    effective_from DATETIME(6) NOT NULL,
    actor CHAR(36) NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (version, revision),
    FOREIGN KEY (version) REFERENCES shipping_policy_versions (version)
);
