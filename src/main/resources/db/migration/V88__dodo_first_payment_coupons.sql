-- Existing coupons remain Efí-only. Dodo uses native one-cycle discounts.
ALTER TABLE upgrade_coupons
    ADD COLUMN provider VARCHAR(30) NOT NULL DEFAULT 'EFI',
    ADD COLUMN native_discount_id VARCHAR(120) NULL;
ALTER TABLE upgrade_coupon_uses
    ADD COLUMN native_discount_id VARCHAR(120) NULL;
