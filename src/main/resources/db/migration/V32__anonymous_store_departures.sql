ALTER TABLE stores ADD COLUMN departure_counted BOOLEAN NOT NULL DEFAULT FALSE;

-- Somente totais por dia: sem store_id, nome, contato ou token.
CREATE TABLE store_departure_daily (
    departure_day DATE PRIMARY KEY,
    uninstall_count BIGINT NOT NULL DEFAULT 0,
    erasure_departure_count BIGINT NOT NULL DEFAULT 0,
    recovered_uninstall_count BIGINT NOT NULL DEFAULT 0,
    recovered_erasure_count BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Preserva as saídas ainda existentes antes da migração, no fuso da sessão SQL.
INSERT INTO store_departure_daily (departure_day, uninstall_count, erasure_departure_count)
SELECT DATE(uninstalled_at), COUNT(*), 0
FROM stores WHERE uninstalled_at IS NOT NULL
GROUP BY DATE(uninstalled_at);

UPDATE stores SET departure_counted = TRUE WHERE uninstalled_at IS NOT NULL;
