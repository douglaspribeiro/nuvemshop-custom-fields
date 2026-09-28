ALTER TABLE payment_subscriptions
    ADD COLUMN technical_error VARCHAR(500) NULL AFTER last_error;
