ALTER TABLE payment_subscriptions ADD COLUMN webhook_downgrade_after TIMESTAMP(6) NULL;
ALTER TABLE payment_subscriptions ADD COLUMN webhook_downgrade_status VARCHAR(30) NULL;
CREATE INDEX ix_payment_webhook_downgrade_after ON payment_subscriptions (webhook_downgrade_after);
UPDATE renewal_failure_emails
SET next_attempt_at = GREATEST(next_attempt_at, DATE_ADD(created_at, INTERVAL 1 HOUR))
WHERE state = 'PENDING';
