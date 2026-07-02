ALTER TABLE stores
    ADD COLUMN store_country_code VARCHAR(2) NULL AFTER store_name,
    ADD COLUMN store_currency VARCHAR(3) NULL AFTER store_country_code;
