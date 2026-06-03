CREATE TABLE plan_events (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id   BIGINT      NOT NULL,
    from_plan  VARCHAR(30),
    to_plan    VARCHAR(30) NOT NULL,
    source     VARCHAR(30) NOT NULL,
    created_at TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    INDEX ix_plan_events_store_created (store_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE feature_flags (
    flag_key    VARCHAR(120) PRIMARY KEY,
    enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    description VARCHAR(500)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
