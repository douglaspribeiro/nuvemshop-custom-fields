ALTER TABLE payment_catalog_prices
    ADD COLUMN validation_error VARCHAR(500) NULL AFTER enabled,
    ADD COLUMN validated_at TIMESTAMP NULL AFTER validation_error;
