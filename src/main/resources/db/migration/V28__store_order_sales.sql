CREATE TABLE store_order_sales (
    store_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    total_items BIGINT NOT NULL DEFAULT 0,
    personalized_items BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (store_id, order_id),
    CONSTRAINT fk_store_order_sales_store FOREIGN KEY (store_id) REFERENCES stores(store_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE store_sales_sync (
    store_id BIGINT NOT NULL PRIMARY KEY,
    complete BOOLEAN NOT NULL DEFAULT FALSE,
    last_attempt_at TIMESTAMP(6) NULL,
    last_synced_at TIMESTAMP(6) NULL,
    last_error VARCHAR(500) NULL,
    CONSTRAINT fk_store_sales_sync_store FOREIGN KEY (store_id) REFERENCES stores(store_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
