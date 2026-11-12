CREATE TABLE template_batches (
 id VARCHAR(36) PRIMARY KEY,
 store_id BIGINT NOT NULL,
 source_key VARCHAR(100) NOT NULL,
 apply_mode VARCHAR(10) NOT NULL,
 total_products INT NOT NULL,
 succeeded_products INT NOT NULL DEFAULT 0,
 document_json LONGTEXT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 INDEX idx_template_batch_store(store_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE template_product_sources (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 store_id BIGINT NOT NULL,
 product_id BIGINT NOT NULL,
 source_key VARCHAR(100) NOT NULL,
 source_revision BIGINT NOT NULL,
 template_json LONGTEXT NOT NULL,
 configuration_json LONGTEXT NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT uk_template_product_source UNIQUE(store_id,product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
