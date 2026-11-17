-- Preserve existing upgrade coupons and their scope; no promotional code is created or enabled.
ALTER TABLE upgrade_coupons
    ADD COLUMN purpose VARCHAR(30) NOT NULL DEFAULT 'UPGRADE';

-- A use belongs either to an upgrade adjustment or to a first subscription checkout.
-- Financial bindings remain durable until the regular recurring price is restored.
ALTER TABLE upgrade_coupon_uses
    MODIFY COLUMN adjustment_id VARCHAR(36) NULL,
    ADD COLUMN payment_reference VARCHAR(64) NULL UNIQUE,
    ADD COLUMN coupon_code VARCHAR(36) NULL,
    ADD COLUMN subscription_id VARCHAR(120) NULL,
    ADD COLUMN first_payment_id VARCHAR(120) NULL,
    ADD COLUMN regular_amount DECIMAL(12,2) NULL,
    ADD COLUMN first_amount DECIMAL(12,2) NULL,
    ADD COLUMN used_at TIMESTAMP(6) NULL,
    ADD COLUMN price_restored_at TIMESTAMP(6) NULL,
    ADD COLUMN recurrence_stopped_at TIMESTAMP(6) NULL;
