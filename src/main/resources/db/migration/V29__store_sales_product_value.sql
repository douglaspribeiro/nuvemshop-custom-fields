ALTER TABLE store_order_sales
    ADD COLUMN created_at TIMESTAMP(6) NULL,
    ADD COLUMN product_value DECIMAL(20, 2) NULL;

CREATE INDEX idx_store_order_sales_created_at ON store_order_sales (created_at, order_id);

ALTER TABLE store_sales_sync
    ADD COLUMN product_value_backfilled BOOLEAN NOT NULL DEFAULT FALSE;
