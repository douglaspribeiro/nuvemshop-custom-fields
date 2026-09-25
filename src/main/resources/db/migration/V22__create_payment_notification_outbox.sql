CREATE TABLE payment_notification_outbox (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider        VARCHAR(30) NOT NULL,
    payment_id      VARCHAR(120) NOT NULL,
    store_id        BIGINT NOT NULL,
    plan            VARCHAR(30) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    amount_value    DECIMAL(12,2) NOT NULL,
    attempts        INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    delivered_at    TIMESTAMP(6) NULL,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_payment_notification_provider_payment (provider, payment_id),
    INDEX ix_payment_notification_due (delivered_at, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
