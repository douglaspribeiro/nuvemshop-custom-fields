ALTER TABLE payment_subscriptions
    ADD COLUMN pending_started_at TIMESTAMP(6) NULL;

-- Registros existentes nao guardavam o inicio da tentativa. Usar a ultima
-- alteracao e conservador: pode dar mais 30 minutos, mas nunca menos.
UPDATE payment_subscriptions
SET pending_started_at = updated_at
WHERE provider = 'EFI' AND status = 'PENDING' AND access_active = FALSE;

CREATE INDEX ix_payment_subscriptions_pending_timeout
    ON payment_subscriptions (provider, status, access_active, pending_started_at);
