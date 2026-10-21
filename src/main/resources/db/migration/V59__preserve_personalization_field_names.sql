CREATE TABLE personalization_field_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    property_name VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    CONSTRAINT uk_field_history UNIQUE (store_id, product_id, property_name),
    CONSTRAINT fk_field_history_store FOREIGN KEY (store_id) REFERENCES stores(store_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO personalization_field_history (store_id, product_id, property_name)
SELECT DISTINCT r.store_id, r.product_id, TRIM(REPLACE(REPLACE(f.label, '[', '('), ']', ')')) COLLATE utf8mb4_bin
FROM personalization_fields f JOIN personalization_rules r ON r.id=f.rule_id;

-- Revisit history with the durable catalog (also includes existing departure snapshots).
UPDATE store_sales_sync SET complete=FALSE, personalized_value_backfilled=FALSE,
    last_attempt_at=NULL, last_error=NULL;
