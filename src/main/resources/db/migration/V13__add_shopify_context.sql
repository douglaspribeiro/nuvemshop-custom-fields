ALTER TABLE personalization_rules
    DROP INDEX uq_store_product,
    ADD COLUMN platform VARCHAR(30) NOT NULL DEFAULT 'NUVEMSHOP' AFTER id,
    ADD UNIQUE KEY uq_platform_store_product (platform, store_id, product_id);

CREATE TABLE shopify_shops (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_domain     VARCHAR(255) NOT NULL UNIQUE,
    shopify_shop_id BIGINT,
    shop_name       VARCHAR(255),
    access_token    TEXT         NOT NULL,
    scope           TEXT,
    plan            VARCHAR(30)  NOT NULL DEFAULT 'FREE',
    installed_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uninstalled_at  TIMESTAMP NULL,
    INDEX ix_shopify_shops_domain_active (shop_domain, uninstalled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
