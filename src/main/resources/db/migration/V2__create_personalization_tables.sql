CREATE TABLE personalization_rules (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id     BIGINT       NOT NULL,
    product_id   BIGINT       NOT NULL,
    product_name VARCHAR(255),
    enabled      BOOLEAN      DEFAULT TRUE,
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_store_product (store_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE personalization_fields (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id     BIGINT       NOT NULL,
    label       VARCHAR(100) NOT NULL,
    field_type  VARCHAR(30)  NOT NULL,
    required    BOOLEAN      DEFAULT FALSE,
    max_length  INT          DEFAULT 100,
    placeholder VARCHAR(150),
    sort_order  INT          DEFAULT 0,
    CONSTRAINT fk_field_rule FOREIGN KEY (rule_id)
        REFERENCES personalization_rules(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
