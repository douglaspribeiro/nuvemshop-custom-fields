-- A assinatura de homologacao 108400 foi confirmada como canceled na API da Efi.
-- O vinculo exato evita alterar uma assinatura de producao ou outra loja.
UPDATE payment_subscriptions
SET status = 'CANCELED',
    provider_status = 'canceled',
    cancellation_pending = FALSE,
    next_payment_at = NULL,
    last_error = NULL,
    last_synced_at = CURRENT_TIMESTAMP(6),
    updated_at = CURRENT_TIMESTAMP(6),
    version = version + 1
WHERE store_id = 5538394
  AND provider = 'EFI'
  AND provider_subscription_id = '108400'
  AND external_reference = 'ncf_5538394_8ec8877d6aef43b68bd13789029786f7'
  AND last_payment_id = '45028398'
  AND status = 'PENDING'
  AND access_active = FALSE;
