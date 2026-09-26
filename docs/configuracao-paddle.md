# Configuração da Paddle

Esta integração nasce em sandbox. Produção só deve ser habilitada depois da
aprovação da conta e de um teste completo em cada mercado.

## Catálogo

No painel Paddle, crie produtos e preços mensais para Essencial e Pro em ARS,
MXN e CLP. Cada preço deve usar imposto incluído (`tax_mode=internal`) e ciclo
mensal com frequência 1. Copie os IDs `pri_...` para **Backoffice → Pagamentos**.
Ao habilitar um preço ou uma rota, o aplicativo consulta a API e bloqueia moeda,
valor, imposto ou recorrência divergentes.

## Variáveis

- `PADDLE_ENABLED=true`
- `PADDLE_SANDBOX=true`
- `PADDLE_API_KEY`
- `PADDLE_CLIENT_SIDE_TOKEN`
- `PADDLE_WEBHOOK_SECRET`
- `PADDLE_API_BASE_URL` (normalmente `https://sandbox-api.paddle.com`)
- `PADDLE_GRACE_DAYS=3`

Segredos não são armazenados nem exibidos no backoffice.

## Webhooks

Cadastre `https://SEU_DOMINIO/prod/webhooks/paddle7`, usando a versão 1, para:

- `transaction.completed`, `transaction.payment_failed`,
  `transaction.past_due`, `transaction.canceled`;
- `subscription.created`, `subscription.updated`, `subscription.activated`,
  `subscription.past_due`, `subscription.paused`, `subscription.resumed`,
  `subscription.canceled`.

O endpoint valida `Paddle-Signature`, persiste o corpo original e responde 200.
Um worker processa a fila a cada cinco segundos. Falhas ficam em
`payment_webhook_events` com status `FAILED`, erro, contador e próxima tentativa;
para reprocessar manualmente, altere `next_attempt_at` para o instante atual,
preservando o mesmo `event_key`.

## Liberação

Preencha e valide primeiro os seis preços sandbox, habilite AR/MX/CL no
backoffice e execute aprovação, recusa, 3DS, renovação, recuperação e
cancelamento. Para produção, troque credenciais/ambiente, cadastre os IDs de
produção e só então habilite as rotas correspondentes.
