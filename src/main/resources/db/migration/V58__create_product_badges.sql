CREATE TABLE product_badges (
    id CHAR(36) PRIMARY KEY,
    code VARCHAR(40) NOT NULL,
    label VARCHAR(60) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    priority INT NOT NULL DEFAULT 100,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uq_product_badges_code (code)
);

CREATE TABLE product_badge_links (
    product_id CHAR(36) NOT NULL,
    badge_id CHAR(36) NOT NULL,
    PRIMARY KEY (product_id, badge_id),
    CONSTRAINT fk_product_badge_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_product_badge_badge FOREIGN KEY (badge_id) REFERENCES product_badges(id),
    INDEX idx_product_badge_links_badge (badge_id, product_id)
);

INSERT INTO product_badges (id, code, label, priority) VALUES
    ('00000000-0000-4000-8000-000000005801', 'NEW_ARRIVAL', 'Hàng mới', 10),
    ('00000000-0000-4000-8000-000000005802', 'BEST_SELLER', 'Bán chạy', 20),
    ('00000000-0000-4000-8000-000000005803', 'TIMELESS_CLASSIC', 'Thiết kế kinh điển', 30),
    ('00000000-0000-4000-8000-000000005804', 'LIMITED_EDITION', 'Phiên bản giới hạn', 40),
    ('00000000-0000-4000-8000-000000005805', 'EXCLUSIVE', 'Độc quyền', 50);
