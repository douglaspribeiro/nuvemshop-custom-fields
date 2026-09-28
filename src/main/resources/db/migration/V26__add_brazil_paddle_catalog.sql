-- A rota brasileira continua em EFI por padrão. Estes preços apenas tornam
-- possível configurar e validar Paddle para BR, sem mudar lojas existentes.
INSERT INTO payment_catalog_prices(provider, environment, country_code, plan, currency, amount_value, tax_mode, recurring, enabled)
VALUES ('PADDLE', 'SANDBOX', 'BR', 'PREMIUM', 'BRL', 19.99, 'internal', TRUE, FALSE),
       ('PADDLE', 'SANDBOX', 'BR', 'PREMIUM_PLUS', 'BRL', 29.99, 'internal', TRUE, FALSE),
       ('PADDLE', 'PRODUCTION', 'BR', 'PREMIUM', 'BRL', 19.99, 'internal', TRUE, FALSE),
       ('PADDLE', 'PRODUCTION', 'BR', 'PREMIUM_PLUS', 'BRL', 29.99, 'internal', TRUE, FALSE);
