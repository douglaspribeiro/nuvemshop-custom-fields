CREATE TABLE creem_catalog_publications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    publication_key VARCHAR(64) NOT NULL UNIQUE,
    reservation_key VARCHAR(64) UNIQUE,
    payload_json TEXT NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    plan VARCHAR(30) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATING',
    product_id VARCHAR(120),
    last_error VARCHAR(500),
    operator_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    lease_until TIMESTAMP(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Rotas e preços Creem são cadastrados pelo administrador; nenhuma rota é ativada.
