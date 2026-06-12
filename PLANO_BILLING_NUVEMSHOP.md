# Billing Nuvemshop + Paginas Publicas

## Summary

Implementar a primeira versao do billing recorrente da Nuvemshop para `PREMIUM` e `PREMIUM_PLUS`, mantendo `FREE` e `Premium Cortesia` fora da cobranca. Tambem adicionar paginas publicas obrigatorias para cadastro/homologacao do app:

- `https://campos-personalizados.wzhub.pro/privacy/`
- `https://campos-personalizados.wzhub.pro/support/`

## Billing

- Adicionar configuracao:
  - `NUVEMSHOP_BILLING_ENABLED=false`
  - `NUVEMSHOP_BILLING_API_BASE_URL=https://api.tiendanube.com/2025-03`
  - `NUVEMSHOP_BILLING_CONCEPT_CODE=app-cost`
  - `NUVEMSHOP_BILLING_CURRENCY=BRL`
  - `NUVEMSHOP_BILLING_PREMIUM_EXTERNAL_ID=PREMIUM`
  - `NUVEMSHOP_BILLING_PREMIUM_PLUS_EXTERNAL_ID=PREMIUM_PLUS`
  - `NUVEMSHOP_BILLING_PREMIUM_AMOUNT=9.99`
  - `NUVEMSHOP_BILLING_PREMIUM_PLUS_AMOUNT=19.99`
- Criar `NuvemshopBillingService`:
  - Ao clicar em um plano pago, garantir o plano remoto na Billing API.
  - Criar/atualizar assinatura da loja via Subscription API.
  - So alterar `store.plan` apos resposta 2xx da Nuvemshop.
  - Bloquear cobranca se `store.courtesyPremium == true`.
- Criar `POST /admin/billing/subscribe`:
  - Aceita apenas `PREMIUM` e `PREMIUM_PLUS`.
  - Redireciona para `/admin/billing` com mensagem de sucesso ou erro.
- Atualizar `/admin/billing`:
  - Mostrar botoes de assinatura quando billing estiver ativo.
  - Manter aviso de cobranca manual quando billing estiver desativado.
  - Para Premium Cortesia, mostrar que o plano esta liberado sem cobranca e esconder botoes pagos.
- Registrar webhooks:
  - `subscription/updated`
  - `app/suspended`
  - `app/resumed`

## Data Model

- Reaproveitar `stores.subscription_id` para referencia externa da assinatura.
- Adicionar migration com campos:
  - `billing_plan_external_id VARCHAR(80)`
  - `billing_amount_currency VARCHAR(3)`
  - `billing_amount_value DECIMAL(10,2)`
  - `billing_next_execution DATE`
  - `billing_last_execution DATE`
  - `billing_suspended BOOLEAN NOT NULL DEFAULT FALSE`
  - `billing_last_synced_at TIMESTAMP NULL`
  - `billing_last_error VARCHAR(500) NULL`
- Se `billing_suspended=true`, tratar a loja como sem acesso premium ate webhook `app/resumed`.

## Public Pages

- Criar controller publico para:
  - `GET /privacy/`
  - `GET /support/`
- Criar templates:
  - `templates/public/privacy.html`
  - `templates/public/support.html`
- Conteudo inicial sera placeholder:
  - Privacy: "Politica de Privacidade" e aviso de conteudo definitivo em breve.
  - Support: "Suporte" e aviso de canais oficiais em breve.
- Adicionar aliases:
  - `/privacy` redireciona para `/privacy/`
  - `/support` redireciona para `/support/`
- Essas paginas nao exigem sessao, OAuth, Nexo ou loja instalada.

## Test Plan

- Billing:
  - Servico garante plano remoto e atualiza subscription.
  - Plano local so muda apos sucesso da API.
  - Falha da API mantem plano anterior e salva erro.
  - Premium Cortesia nao chama Billing API.
  - Webhooks `subscription/updated`, `app/suspended`, `app/resumed` sincronizam estado.
- Public pages:
  - `GET /privacy/` retorna `200`.
  - `GET /support/` retorna `200`.
  - Aliases sem barra redirecionam corretamente.
- Regression:
  - `git diff --check`
  - `mvn test`
  - Verificar `/actuator/health`

## Assumptions

- A v1 cobre apenas assinaturas recorrentes, nao cobrancas variaveis via Charges API.
- Downgrade para `FREE` continua pelo backoffice.
- Planos remotos sao garantidos no momento do upgrade, nao no startup.
- O billing recorrente de aplicativos usa `NUVEMSHOP_BILLING_CONCEPT_CODE=app-cost`.
