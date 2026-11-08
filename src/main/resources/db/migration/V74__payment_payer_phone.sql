ALTER TABLE payment_subscriptions
    ADD COLUMN payer_phone VARCHAR(20) NULL AFTER payer_email;
