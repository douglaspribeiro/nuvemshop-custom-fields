ALTER TABLE buyer_uploads
    ADD COLUMN product_name VARCHAR(512) NULL,
    ADD COLUMN product_image_url VARCHAR(1024) NULL,
    ADD COLUMN order_number VARCHAR(64) NULL,
    ADD COLUMN order_status VARCHAR(40) NULL,
    ADD COLUMN payment_status VARCHAR(40) NULL,
    ADD COLUMN shipping_status VARCHAR(40) NULL,
    ADD COLUMN metadata_checked_at TIMESTAMP NULL;
