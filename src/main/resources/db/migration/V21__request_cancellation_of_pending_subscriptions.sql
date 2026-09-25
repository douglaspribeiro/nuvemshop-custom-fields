-- A limpeza local so termina depois que a conciliacao cancelar a assinatura remota.
-- Sem ID remoto, a aplicacao nao consegue garantir que a cobranca foi interrompida.
-- Tentativas sem assinatura, checkout ou cobranca conhecidos localmente nao tem
-- pagamento associado que o aplicativo consiga conciliar ou cancelar.
UPDATE payment_subscriptions
SET status = 'CANCELED',
    provider_status = 'canceled',
    last_error = NULL,
    next_payment_at = NULL,
    last_synced_at = CURRENT_TIMESTAMP(6),
    updated_at = CURRENT_TIMESTAMP(6),
    version = version + 1
WHERE status = 'PENDING'
  AND access_active = FALSE
  AND provider_subscription_id IS NULL
  AND provider_checkout_id IS NULL
  AND checkout_url IS NULL
  AND last_payment_id IS NULL;

UPDATE payment_subscriptions
SET cancellation_pending = TRUE,
    updated_at = CURRENT_TIMESTAMP(6),
    version = version + 1
WHERE status = 'PENDING'
  AND access_active = FALSE
  AND provider_subscription_id IS NOT NULL
  AND cancellation_pending = FALSE;
