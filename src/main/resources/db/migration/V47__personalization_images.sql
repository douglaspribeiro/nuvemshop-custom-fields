ALTER TABLE personalization_fields ADD COLUMN image_options_json TEXT;

-- Sem FK: a fila de exclusao deve sobreviver a remocao da loja/campo.
CREATE TABLE personalization_images (
    id VARCHAR(36) PRIMARY KEY,
    store_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    field_id BIGINT,
    bucket VARCHAR(255) NOT NULL,
    preview_key VARCHAR(512) NOT NULL,
    thumbnail_key VARCHAR(512) NOT NULL,
    state VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    retry_at TIMESTAMP NOT NULL,
    last_error VARCHAR(500),
    INDEX ix_personalization_images_cleanup (state, retry_at),
    INDEX ix_personalization_images_owner (store_id, product_id, field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
