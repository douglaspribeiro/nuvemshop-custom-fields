ALTER TABLE plan_assets
    ADD COLUMN image_product_limit BIGINT NOT NULL DEFAULT 0;

ALTER TABLE plan_assets
    ADD COLUMN image_option_limit INT NOT NULL DEFAULT 0;

UPDATE plan_assets SET image_product_limit = 1, image_option_limit = 3 WHERE plan_type = 'PREMIUM';
UPDATE plan_assets SET image_product_limit = product_limit, image_option_limit = 8 WHERE plan_type = 'PREMIUM_PLUS';
UPDATE plan_assets SET image_product_limit = product_limit, image_option_limit = 25 WHERE plan_type = 'PREMIUM_ULTRA';
