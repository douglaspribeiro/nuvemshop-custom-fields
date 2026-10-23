-- Catálogo inicial editável para Argentina. Não altera rotas ou contratos existentes.
-- Valores ARS seguem o catálogo regional já definido na aplicação; confirmar antes de ativar.
INSERT INTO payment_catalog_prices
    (provider, environment, country_code, plan, currency, amount_value,
     provider_price_id, tax_mode, recurring, enabled)
VALUES
    ('DLOCAL_GO', 'SANDBOX', 'AR', 'PREMIUM', 'ARS', 5599.00, NULL, 'internal', TRUE, FALSE),
    ('DLOCAL_GO', 'SANDBOX', 'AR', 'PREMIUM_PLUS', 'ARS', 8399.00, NULL, 'internal', TRUE, FALSE),
    ('DLOCAL_GO', 'SANDBOX', 'AR', 'PREMIUM_ULTRA', 'ARS', 16799.00, NULL, 'internal', TRUE, FALSE),
    ('DLOCAL_GO', 'PRODUCTION', 'AR', 'PREMIUM', 'ARS', 5599.00, NULL, 'internal', TRUE, FALSE),
    ('DLOCAL_GO', 'PRODUCTION', 'AR', 'PREMIUM_PLUS', 'ARS', 8399.00, NULL, 'internal', TRUE, FALSE),
    ('DLOCAL_GO', 'PRODUCTION', 'AR', 'PREMIUM_ULTRA', 'ARS', 16799.00, NULL, 'internal', TRUE, FALSE);
