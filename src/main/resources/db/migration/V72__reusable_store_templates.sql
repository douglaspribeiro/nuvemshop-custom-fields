CREATE TABLE store_templates (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    origin VARCHAR(100) NOT NULL,
    fields_json LONGTEXT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    INDEX idx_template_store (store_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Each application is one atomic, replayable product operation. No buyer data.
CREATE TABLE template_applications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    source_key VARCHAR(100) NOT NULL,
    product_id BIGINT NOT NULL,
    apply_mode VARCHAR(12) NOT NULL,
    review_token VARCHAR(64) NOT NULL,
    field_count INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_template_application UNIQUE (store_id, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Shared immutable merchant images are retained while any field/template uses them.
-- No owner FK: deletion intents and reconciliation must survive owner removal.
CREATE TABLE personalization_image_references (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    image_id VARCHAR(36) NOT NULL,
    store_id BIGINT NOT NULL,
    owner_type VARCHAR(12) NOT NULL,
    owner_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    CONSTRAINT uk_image_reference UNIQUE (image_id, owner_type, owner_id),
    INDEX idx_image_reference_owner (store_id, owner_type, owner_id),
    INDEX idx_image_reference_product (store_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO personalization_image_references (image_id,store_id,owner_type,owner_id,product_id)
SELECT id,store_id,'FIELD',field_id,product_id FROM personalization_images WHERE field_id IS NOT NULL AND state='READY';
