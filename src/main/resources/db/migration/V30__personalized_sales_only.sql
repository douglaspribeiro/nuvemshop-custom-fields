ALTER TABLE store_order_sales
    RENAME COLUMN product_value TO personalized_product_value;

UPDATE store_order_sales
SET personalized_product_value = NULL
WHERE personalized_product_value IS NOT NULL;

ALTER TABLE store_sales_sync
    RENAME COLUMN product_value_backfilled TO personalized_value_backfilled;

UPDATE store_sales_sync
SET personalized_value_backfilled = FALSE;
