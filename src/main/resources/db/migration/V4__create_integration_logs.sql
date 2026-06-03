CREATE TABLE integration_logs (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id   BIGINT,
    level      VARCHAR(20)  NOT NULL,
    event_type VARCHAR(80)  NOT NULL,
    message    VARCHAR(500) NOT NULL,
    created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX ix_integration_logs_store_created (store_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
