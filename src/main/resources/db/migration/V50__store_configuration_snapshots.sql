CREATE TABLE store_configuration_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    uninstalled_at TIMESTAMP(6) NOT NULL,
    configuration_json LONGTEXT NOT NULL,
    departure_reason VARCHAR(160) NULL,
    departure_justification VARCHAR(2000) NULL,
    CONSTRAINT uk_configuration_departure UNIQUE (store_id, uninstalled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
