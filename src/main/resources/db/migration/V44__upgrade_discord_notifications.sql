ALTER TABLE payment_notification_outbox ADD COLUMN event_type VARCHAR(20) NOT NULL DEFAULT 'PAYMENT';
ALTER TABLE payment_notification_outbox ADD COLUMN store_name VARCHAR(255) NULL;
ALTER TABLE payment_notification_outbox ADD COLUMN source_plan VARCHAR(30) NULL;
ALTER TABLE payment_notification_outbox ADD COLUMN coupon_code VARCHAR(36) NULL;
ALTER TABLE payment_notification_outbox ADD COLUMN subscription_id VARCHAR(120) NULL;
ALTER TABLE payment_notification_outbox ADD COLUMN charge_id VARCHAR(120) NULL;
ALTER TABLE payment_notification_outbox ADD COLUMN recurring_amount DECIMAL(12,2) NULL;
