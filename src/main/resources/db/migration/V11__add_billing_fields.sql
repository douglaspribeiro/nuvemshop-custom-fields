ALTER TABLE stores
    ADD COLUMN billing_plan_external_id VARCHAR(80),
    ADD COLUMN billing_amount_currency VARCHAR(3),
    ADD COLUMN billing_amount_value DECIMAL(10,2),
    ADD COLUMN billing_next_execution DATE,
    ADD COLUMN billing_last_execution DATE,
    ADD COLUMN billing_suspended BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN billing_last_synced_at TIMESTAMP NULL,
    ADD COLUMN billing_last_error VARCHAR(500) NULL;
