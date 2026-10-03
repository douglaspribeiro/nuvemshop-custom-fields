CREATE TABLE upgrade_coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(36) NOT NULL UNIQUE,
    discount_percent DECIMAL(5,2) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    target_plan VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    starts_at TIMESTAMP NULL,
    ends_at TIMESTAMP NULL,
    max_uses INTEGER NULL,
    max_uses_per_store INTEGER NOT NULL DEFAULT 1,
    used_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
ALTER TABLE upgrade_adjustments ADD COLUMN coupon_id BIGINT NULL;
ALTER TABLE upgrade_adjustments ADD COLUMN coupon_percent DECIMAL(5,2) NULL;
-- Preserva o desconto dos resumos históricos; não cadastra nem habilita BRINDE.
UPDATE upgrade_adjustments SET coupon_percent=30.00 WHERE coupon_code IS NOT NULL;
CREATE TABLE upgrade_coupon_uses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    coupon_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    adjustment_id VARCHAR(36) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_upgrade_coupon_uses_store ON upgrade_coupon_uses(coupon_id,store_id,status);
