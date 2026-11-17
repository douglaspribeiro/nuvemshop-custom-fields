-- Existing winback coupons remain Efí-only. Dodo intent is committed before native creation.
ALTER TABLE winback_coupons
    ADD COLUMN provider VARCHAR(30) NOT NULL DEFAULT 'EFI',
    ADD COLUMN provider_environment VARCHAR(20) NULL,
    ADD COLUMN native_discount_id VARCHAR(120) NULL,
    ADD COLUMN native_sync_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN native_sync_after TIMESTAMP(6) NULL,
    ADD COLUMN native_confirmed_at TIMESTAMP(6) NULL,
    ADD COLUMN native_sync_error VARCHAR(255) NULL;
CREATE INDEX idx_winback_coupon_reference ON winback_coupons(external_reference);
