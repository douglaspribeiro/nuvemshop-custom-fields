-- Produtos mensais criados e conferidos via API Dodo de TESTE em 2026-10-15 15:01:22 UTC.
-- Não cria contratos de clientes, não altera rotas e não habilita preços.
-- Vínculos já existentes são preservados integralmente.
INSERT INTO payment_catalog_prices
    (provider, environment, country_code, plan, currency, amount_value,
     provider_price_id, tax_mode, recurring, enabled, validation_error, validated_at)
VALUES
    ('DODO_TEST', 'SANDBOX', 'AR', 'PREMIUM', 'USD', 4.99, 'pdt_0NpncfPHBuCeAoWYEMKWi', 'external', TRUE, FALSE, NULL, '2026-10-15 15:01:22'),
    ('DODO_TEST', 'SANDBOX', 'CO', 'PREMIUM', 'USD', 4.99, 'pdt_0NpncfPHBuCeAoWYEMKWi', 'external', TRUE, FALSE, NULL, '2026-10-15 15:01:22'),
    ('DODO_TEST', 'SANDBOX', 'CL', 'PREMIUM', 'CLP', 4199, 'pdt_0NpncfPHBuCeAoWYEMKWi', 'external', TRUE, FALSE, NULL, '2026-10-15 15:01:22'),
    ('DODO_TEST', 'SANDBOX', 'MX', 'PREMIUM', 'MXN', 99.00, 'pdt_0NpncfPHBuCeAoWYEMKWi', 'external', TRUE, FALSE, NULL, '2026-10-15 15:01:22'),
    ('DODO_TEST', 'SANDBOX', 'AR', 'PREMIUM_PLUS', 'USD', 7.49, 'pdt_0NpncfTZsACz359krtMyz', 'external', TRUE, FALSE, NULL, '2026-10-15 15:01:22'),
    ('DODO_TEST', 'SANDBOX', 'CO', 'PREMIUM_PLUS', 'USD', 7.49, 'pdt_0NpncfTZsACz359krtMyz', 'external', TRUE, FALSE, NULL, '2026-10-15 15:01:22'),
    ('DODO_TEST', 'SANDBOX', 'CL', 'PREMIUM_PLUS', 'CLP', 6299, 'pdt_0NpncfTZsACz359krtMyz', 'external', TRUE, FALSE, NULL, '2026-10-15 15:01:22'),
    ('DODO_TEST', 'SANDBOX', 'MX', 'PREMIUM_PLUS', 'MXN', 149.00, 'pdt_0NpncfTZsACz359krtMyz', 'external', TRUE, FALSE, NULL, '2026-10-15 15:01:22'),
    ('DODO_TEST', 'SANDBOX', 'AR', 'PREMIUM_ULTRA', 'USD', 14.99, 'pdt_0NpncfWWeuij8hUmcu1vb', 'external', TRUE, FALSE, NULL, '2026-10-15 15:01:22'),
    ('DODO_TEST', 'SANDBOX', 'CO', 'PREMIUM_ULTRA', 'USD', 14.99, 'pdt_0NpncfWWeuij8hUmcu1vb', 'external', TRUE, FALSE, NULL, '2026-10-15 15:01:22'),
    ('DODO_TEST', 'SANDBOX', 'CL', 'PREMIUM_ULTRA', 'CLP', 12599, 'pdt_0NpncfWWeuij8hUmcu1vb', 'external', TRUE, FALSE, NULL, '2026-10-15 15:01:22'),
    ('DODO_TEST', 'SANDBOX', 'MX', 'PREMIUM_ULTRA', 'MXN', 299.00, 'pdt_0NpncfWWeuij8hUmcu1vb', 'external', TRUE, FALSE, NULL, '2026-10-15 15:01:22')
ON DUPLICATE KEY UPDATE id = id;
