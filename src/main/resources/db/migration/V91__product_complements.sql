CREATE TABLE product_complement_offers (
    id VARCHAR(36) PRIMARY KEY,
    store_id BIGINT NOT NULL,
    parent_product_id BIGINT NOT NULL,
    parent_name VARCHAR(255) NOT NULL,
    complement_product_id BIGINT,
    complement_variant_id BIGINT,
    complement_name VARCHAR(255) NOT NULL,
    label VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    creation_state VARCHAR(12) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_complement_pair UNIQUE (store_id, parent_product_id, complement_variant_id),
    CONSTRAINT fk_complement_store FOREIGN KEY (store_id) REFERENCES stores(store_id) ON DELETE CASCADE,
    INDEX idx_complement_store (store_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
