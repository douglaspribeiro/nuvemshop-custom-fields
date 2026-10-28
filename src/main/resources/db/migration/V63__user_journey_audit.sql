CREATE TABLE user_journey_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    journey_id VARCHAR(36) NOT NULL,
    actor VARCHAR(24) NOT NULL,
    action VARCHAR(160) NOT NULL,
    method VARCHAR(8) NOT NULL,
    route VARCHAR(200) NOT NULL,
    product_id BIGINT NULL,
    field_id BIGINT NULL,
    selected_plan VARCHAR(40) NULL,
    outcome VARCHAR(24) NOT NULL,
    http_status INT NULL,
    before_state LONGTEXT NULL,
    after_state LONGTEXT NULL,
    INDEX idx_journey_store_date (store_id, occurred_at, id),
    INDEX idx_journey_date (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- No retention job in V1. occurred_at supports a future 90-day cleanup.
