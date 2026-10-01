-- A comparação com a outbox exige a mesma precisão nos dois timestamps.
ALTER TABLE stores MODIFY COLUMN uninstalled_at TIMESTAMP(6) NULL;

CREATE TABLE winback_outbox (
    id VARCHAR(36) PRIMARY KEY,
    store_id BIGINT NOT NULL,
    uninstalled_at TIMESTAMP(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6) NULL,
    CONSTRAINT uk_winback_store_uninstall UNIQUE (store_id, uninstalled_at),
    INDEX idx_winback_outbox_due (published_at, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
