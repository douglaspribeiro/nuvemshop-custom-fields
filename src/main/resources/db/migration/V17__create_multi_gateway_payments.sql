ALTER TABLE stores
    ADD COLUMN store_email VARCHAR(255) NULL;

CREATE TABLE payment_subscriptions (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id                 BIGINT       NOT NULL UNIQUE,
    provider                 VARCHAR(30)  NOT NULL,
    provider_subscription_id VARCHAR(120) UNIQUE,
    external_reference       VARCHAR(64)  NOT NULL UNIQUE,
    checkout_url             TEXT,
    plan                     VARCHAR(30)  NOT NULL,
    currency                 VARCHAR(3)   NOT NULL,
    amount_value             DECIMAL(12,2) NOT NULL,
    status                   VARCHAR(30)  NOT NULL,
    provider_status          VARCHAR(50),
    access_active            BOOLEAN      NOT NULL DEFAULT FALSE,
    next_payment_at          TIMESTAMP(6) NULL,
    last_payment_id          VARCHAR(120),
    last_payment_status      VARCHAR(50),
    grace_until              TIMESTAMP(6) NULL,
    cancellation_pending     BOOLEAN      NOT NULL DEFAULT FALSE,
    last_synced_at           TIMESTAMP(6) NULL,
    last_error               VARCHAR(500),
    version                  BIGINT       NOT NULL DEFAULT 0,
    created_at               TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at               TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX ix_payment_subscriptions_provider_status (provider, status),
    INDEX ix_payment_subscriptions_cancellation (cancellation_pending)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment_webhook_events (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id            BIGINT NULL,
    provider            VARCHAR(30)  NOT NULL,
    event_key           VARCHAR(190) NOT NULL UNIQUE,
    event_type          VARCHAR(100) NOT NULL,
    provider_resource_id VARCHAR(120),
    status              VARCHAR(30)  NOT NULL,
    processing_attempts INT          NOT NULL DEFAULT 0,
    processed_at        TIMESTAMP(6) NULL,
    last_error          VARCHAR(500),
    received_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX ix_payment_webhook_status_received (status, received_at),
    INDEX ix_payment_webhook_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
