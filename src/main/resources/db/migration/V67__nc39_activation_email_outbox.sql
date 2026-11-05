CREATE TABLE nc39_activation_email_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    installed_at TIMESTAMP(6) NOT NULL,
    step VARCHAR(12) NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    sent_at TIMESTAMP(6) NULL,
    suppression_reason VARCHAR(80) NULL,
    template_version VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_nc39_store_install_step UNIQUE (store_id, installed_at, step),
    INDEX ix_nc39_due (state, next_attempt_at),
    INDEX ix_nc39_store (store_id, installed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
