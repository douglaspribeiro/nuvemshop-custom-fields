CREATE TABLE payment_routing_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    country_code VARCHAR(2) NOT NULL UNIQUE,
    provider VARCHAR(30) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment_routing_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    country_code VARCHAR(2) NOT NULL,
    old_provider VARCHAR(30),
    new_provider VARCHAR(30) NOT NULL,
    old_environment VARCHAR(20),
    new_environment VARCHAR(20) NOT NULL,
    old_enabled BOOLEAN,
    new_enabled BOOLEAN NOT NULL,
    operator_name VARCHAR(120) NOT NULL,
    changed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX ix_payment_routing_history_country_changed (country_code, changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment_catalog_prices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider VARCHAR(30) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    plan VARCHAR(30) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount_value DECIMAL(12,2) NOT NULL,
    provider_price_id VARCHAR(120),
    tax_mode VARCHAR(20) NOT NULL DEFAULT 'internal',
    recurring BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_payment_catalog_market_plan (provider, environment, country_code, plan)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    provider VARCHAR(30) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    plan VARCHAR(30) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount_value DECIMAL(12,2) NOT NULL,
    external_reference VARCHAR(64) NOT NULL UNIQUE,
    provider_transaction_id VARCHAR(120),
    checkout_token_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    last_error VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_payment_attempt_provider_transaction (provider, environment, provider_transaction_id),
    INDEX ix_payment_attempt_store_status (store_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE payment_subscriptions
    ADD COLUMN provider_customer_id VARCHAR(120) NULL AFTER provider_checkout_id,
    ADD COLUMN provider_price_id VARCHAR(120) NULL AFTER provider_customer_id,
    ADD COLUMN country_code VARCHAR(2) NULL AFTER provider_price_id,
    ADD COLUMN provider_environment VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION' AFTER country_code,
    ADD COLUMN current_period_start TIMESTAMP(6) NULL AFTER next_payment_at,
    ADD COLUMN current_period_end TIMESTAMP(6) NULL AFTER current_period_start,
    ADD COLUMN cancellation_requested_at TIMESTAMP(6) NULL AFTER cancellation_pending,
    ADD COLUMN cancellation_effective_at TIMESTAMP(6) NULL AFTER cancellation_requested_at;

ALTER TABLE payment_webhook_events
    ADD COLUMN notification_id VARCHAR(120) NULL AFTER event_key,
    ADD COLUMN provider_environment VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION' AFTER provider,
    ADD COLUMN occurred_at TIMESTAMP(6) NULL AFTER event_type,
    ADD COLUMN payload_json JSON NULL AFTER provider_resource_id,
    ADD COLUMN next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) AFTER processing_attempts;

CREATE UNIQUE INDEX uk_payment_webhook_provider_notification
    ON payment_webhook_events (provider, provider_environment, notification_id);
CREATE INDEX ix_payment_webhook_due
    ON payment_webhook_events (status, next_attempt_at);

INSERT INTO payment_routing_rules(country_code, provider, environment, enabled, updated_by)
VALUES ('BR', 'EFI', 'PRODUCTION', TRUE, 'migration'),
       ('AR', 'PADDLE', 'SANDBOX', TRUE, 'migration'),
       ('MX', 'PADDLE', 'SANDBOX', TRUE, 'migration'),
       ('CL', 'PADDLE', 'SANDBOX', TRUE, 'migration');

INSERT INTO payment_catalog_prices(provider, environment, country_code, plan, currency, amount_value, tax_mode, recurring, enabled)
VALUES ('EFI', 'PRODUCTION', 'BR', 'PREMIUM', 'BRL', 19.99, 'internal', TRUE, TRUE),
       ('EFI', 'PRODUCTION', 'BR', 'PREMIUM_PLUS', 'BRL', 29.99, 'internal', TRUE, TRUE),
       ('PADDLE', 'SANDBOX', 'AR', 'PREMIUM', 'ARS', 5599.00, 'internal', TRUE, TRUE),
       ('PADDLE', 'SANDBOX', 'AR', 'PREMIUM_PLUS', 'ARS', 8399.00, 'internal', TRUE, TRUE),
       ('PADDLE', 'SANDBOX', 'MX', 'PREMIUM', 'MXN', 99.00, 'internal', TRUE, TRUE),
       ('PADDLE', 'SANDBOX', 'MX', 'PREMIUM_PLUS', 'MXN', 149.00, 'internal', TRUE, TRUE),
       ('PADDLE', 'SANDBOX', 'CL', 'PREMIUM', 'CLP', 4199.00, 'internal', TRUE, TRUE),
       ('PADDLE', 'SANDBOX', 'CL', 'PREMIUM_PLUS', 'CLP', 6299.00, 'internal', TRUE, TRUE);

UPDATE payment_subscriptions
SET country_code = 'BR', provider_environment = 'PRODUCTION'
WHERE country_code IS NULL;
