-- O tipo do plano é armazenado como texto; a nova opção não requer ALTER TABLE.
ALTER TABLE support_tickets
    ADD COLUMN plan_at_open VARCHAR(30) NULL AFTER store_id;

-- Os preços Ultra são criados desabilitados: o operador deve informar e validar
-- o ID remoto de cada provedor antes de disponibilizar a assinatura.
INSERT INTO payment_catalog_prices(provider, environment, country_code, plan, currency, amount_value, tax_mode, recurring, enabled)
SELECT provider, environment, country_code, 'PREMIUM_ULTRA', currency,
       CASE country_code
           WHEN 'BR' THEN 59.90
           WHEN 'AR' THEN 16799.00
           WHEN 'CL' THEN 12599.00
           WHEN 'MX' THEN 299.00
           ELSE amount_value * 2
       END,
       tax_mode, TRUE, FALSE
FROM payment_catalog_prices
WHERE plan = 'PREMIUM_PLUS'
  AND NOT EXISTS (
      SELECT 1 FROM payment_catalog_prices ultra
      WHERE ultra.provider = payment_catalog_prices.provider
        AND ultra.environment = payment_catalog_prices.environment
        AND ultra.country_code = payment_catalog_prices.country_code
        AND ultra.plan = 'PREMIUM_ULTRA'
  );
