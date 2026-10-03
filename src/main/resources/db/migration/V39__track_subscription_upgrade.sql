ALTER TABLE payment_subscriptions
    ADD COLUMN upgrade_plan VARCHAR(30) NULL;
ALTER TABLE payment_subscriptions
    ADD COLUMN upgrade_amount DECIMAL(12,2) NULL;
ALTER TABLE payment_subscriptions
    ADD COLUMN upgrade_price_id VARCHAR(120) NULL;
