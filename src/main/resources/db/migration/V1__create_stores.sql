CREATE TABLE stores (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id        BIGINT       NOT NULL UNIQUE,
    access_token    TEXT         NOT NULL,
    scope           TEXT,
    plan            VARCHAR(30)  NOT NULL DEFAULT 'FREE',
    subscription_id VARCHAR(100),
    installed_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    uninstalled_at  TIMESTAMP    NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
