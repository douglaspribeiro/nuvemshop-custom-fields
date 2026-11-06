ALTER TABLE payment_subscriptions
    ADD COLUMN renewal_failure_detected_at TIMESTAMP(6) NULL;
ALTER TABLE payment_subscriptions
    ADD COLUMN renewal_failure_payment_id VARCHAR(120) NULL;

CREATE TABLE renewal_failure_emails (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    provider VARCHAR(30) NOT NULL,
    subscription_id VARCHAR(120) NOT NULL,
    payment_id VARCHAR(120) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    UNIQUE KEY uk_renewal_failure_email (provider, subscription_id, payment_id),
    INDEX ix_renewal_failure_email_due (state, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
