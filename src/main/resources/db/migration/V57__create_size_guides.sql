CREATE TABLE size_guides (
    id CHAR(36) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    content JSON NOT NULL
);

CREATE TABLE size_guide_assignments (
    scope_key VARCHAR(96) PRIMARY KEY,
    guide_id CHAR(36) NOT NULL,
    scope VARCHAR(20) NOT NULL,
    product_id CHAR(36),
    category_id CHAR(36),
    brand_id CHAR(36),
    priority INT NOT NULL DEFAULT 100,
    CONSTRAINT fk_size_guide_assignment_guide FOREIGN KEY (guide_id) REFERENCES size_guides(id),
    CONSTRAINT fk_size_guide_assignment_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_size_guide_assignment_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT fk_size_guide_assignment_brand FOREIGN KEY (brand_id) REFERENCES brands(id),
    CONSTRAINT ck_size_guide_assignment_target CHECK (
        (scope = 'PRODUCT' AND product_id IS NOT NULL AND category_id IS NULL AND brand_id IS NULL)
        OR (scope = 'CATEGORY_BRAND' AND product_id IS NULL AND category_id IS NOT NULL AND brand_id IS NOT NULL)
        OR (scope = 'BRAND' AND product_id IS NULL AND category_id IS NULL AND brand_id IS NOT NULL)
        OR (scope = 'CATEGORY' AND product_id IS NULL AND category_id IS NOT NULL AND brand_id IS NULL)
        OR (scope = 'SHOP' AND product_id IS NULL AND category_id IS NULL AND brand_id IS NULL)
    ),
    INDEX idx_size_guide_assignments_guide (guide_id, scope_key)
);
