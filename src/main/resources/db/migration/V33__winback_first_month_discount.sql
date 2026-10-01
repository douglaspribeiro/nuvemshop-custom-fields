ALTER TABLE winback_campaigns
    ADD COLUMN paid_before_departure BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN converted_at TIMESTAMP(6) NULL,
    ADD COLUMN feature_status VARCHAR(30) NULL;

UPDATE winback_campaigns SET feature_status = 'NOVA' WHERE reason = 'MISSING_FEATURE';

UPDATE winback_campaigns c SET paid_before_departure = TRUE
WHERE EXISTS (SELECT 1 FROM payment_notification_outbox n WHERE n.store_id = c.store_id)
   OR EXISTS (SELECT 1 FROM payment_subscriptions s WHERE s.store_id = c.store_id
       AND LOWER(s.last_payment_status) IN ('paid', 'settled', 'approved'))
   OR EXISTS (SELECT 1 FROM plan_events p WHERE p.store_id = c.store_id
       AND p.to_plan IN ('PREMIUM', 'PREMIUM_PLUS')
       AND p.source IN ('EFI_CHECKOUT', 'PAYMENT_WEBHOOK', 'PAYMENT_RECONCILE', 'PENDING_TIMEOUT_RECONCILE'));

CREATE TABLE winback_coupons (
    code VARCHAR(36) PRIMARY KEY,
    store_id BIGINT NOT NULL UNIQUE,
    campaign_id VARCHAR(36) NOT NULL UNIQUE,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    checkout_at TIMESTAMP(6) NULL,
    external_reference VARCHAR(64) NULL,
    subscription_id VARCHAR(120) NULL,
    first_payment_id VARCHAR(120) NULL,
    used_at TIMESTAMP(6) NULL,
    regular_amount DECIMAL(12,2) NULL,
    first_amount DECIMAL(12,2) NULL,
    price_restored_at TIMESTAMP(6) NULL,
    recurrence_stopped_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_coupon_store FOREIGN KEY (store_id) REFERENCES stores(store_id),
    CONSTRAINT fk_coupon_campaign FOREIGN KEY (campaign_id) REFERENCES winback_campaigns(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE payment_subscriptions
    ADD COLUMN winback_coupon_code VARCHAR(36) NULL,
    ADD COLUMN winback_regular_amount DECIMAL(12,2) NULL,
    ADD COLUMN winback_initial_amount DECIMAL(12,2) NULL,
    ADD COLUMN winback_first_payment_id VARCHAR(120) NULL,
    ADD COLUMN winback_restore_pending BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_subscription_winback_restore ON payment_subscriptions (winback_restore_pending);

-- Efí plan IDs are shared by many subscriptions, unlike unique checkout resource IDs.
UPDATE payment_subscriptions SET provider_price_id = provider_checkout_id, provider_checkout_id = NULL
WHERE provider = 'EFI' AND provider_checkout_id IS NOT NULL;
